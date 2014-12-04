/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2014 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.formats.in;

import java.io.IOException;
import java.util.ArrayList;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;
import loci.common.xml.BaseHandler;
import loci.common.xml.XMLTools;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.Timestamp;

import org.xml.sax.Attributes;

import ome.units.quantity.Time;
import ome.units.UNITS;

/**
 * BioRadSCNReader is the reader for Bio-Rad .scn files
 */
public class BioRadSCNReader extends FormatReader {

  // -- Constants --

  private static final String MAGIC = "Generated by Image Lab";

  // -- Fields --

  private long pixelsOffset;

  private Double gain;
  private Double exposureTime;
  private String imageName;
  private String serialNumber;
  private String acquisitionDate;
  private String binning;
  private String model;
  private Double physicalSizeX, physicalSizeY;

  // -- Constructor --

  /** Constructs a new Bio-Rad .scn reader. */
  public BioRadSCNReader() {
    super("Bio-Rad SCN", "scn");
    domains = new String[] {FormatTools.GEL_DOMAIN};
    suffixSufficient = false;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    final int blockLen = 64;
    if (!FormatTools.validStream(stream, blockLen, false)) return false;
    String check = stream.readString(blockLen);
    return check.indexOf(MAGIC) >= 0;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    in.seek(pixelsOffset);
    readPlane(in, x, y, w, h, buf);
    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    pixelsOffset = 0;
    gain = null;
    exposureTime = null;
    imageName = null;
    serialNumber = null;
    acquisitionDate = null;
    binning = null;
    model = null;
    physicalSizeX = null;
    physicalSizeY = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    in = new RandomAccessInputStream(id);
    CoreMetadata m = core.get(0);

    String line = in.readLine();
    String currentBoundary = "";
    String currentType = "";
    int currentLength = 0;
    ArrayList<String> xml = new ArrayList<String>();
    while (in.getFilePointer() < in.length() && line != null) {
      line = line.trim();
      if (line.startsWith("Content-Type")) {
        currentType = line.substring(line.indexOf(" ") + 1);

        int boundary = currentType.indexOf("boundary");
        if (boundary > 0) {
          currentBoundary =
            currentType.substring(boundary + 10, currentType.length() - 1);
        }

        if (currentType.indexOf(";") > 0) {
          currentType = currentType.substring(0, currentType.indexOf(";"));
        }
      }
      else if (line.equals("--" + currentBoundary)) {
        currentLength = 0;
      }
      else if (line.startsWith("Content-Length")) {
        currentLength = Integer.parseInt(line.substring(line.indexOf(" ") + 1));
      }
      else if (line.length() == 0) {
        if (currentType.equals("application/octet-stream")) {
          pixelsOffset = in.getFilePointer();
          in.skipBytes(currentLength);
        }
        else if (currentType.equals("text/xml")) {
          String xmlBlock = in.readString(currentLength);
          xml.add(xmlBlock);
        }
      }

      line = in.readLine();
    }

    SCNHandler handler = new SCNHandler();
    for (String block : xml) {
      XMLTools.parseXML(block, handler);
    }

    m.sizeZ = 1;
    m.sizeT = 1;
    m.imageCount = 1;
    m.dimensionOrder = "XYCZT";

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this, exposureTime != null);

    store.setInstrumentID(MetadataTools.createLSID("Instrument", 0), 0);

    if (serialNumber != null) {
      store.setMicroscopeSerialNumber(serialNumber, 0);
    }
    if (model != null) {
      store.setMicroscopeModel(model, 0);
    }

    if (imageName != null) {
      store.setImageName(imageName, 0);
    }
    if (acquisitionDate != null) {
      store.setImageAcquisitionDate(new Timestamp(acquisitionDate), 0);
    }

    if (gain != null || binning != null) {
      String detector = MetadataTools.createLSID("Detector", 0, 0);
      store.setDetectorID(detector, 0, 0);
      store.setDetectorSettingsID(detector, 0, 0);
    }

    if (gain != null) {
      store.setDetectorSettingsGain(gain, 0, 0);
    }
    if (binning != null) {
      store.setDetectorSettingsBinning(getBinning(binning), 0, 0);
    }

    if (exposureTime != null) {
      store.setPlaneExposureTime(new Time(exposureTime, UNITS.S), 0, 0);
    }

    if (physicalSizeX != null) {
      store.setPixelsPhysicalSizeX(FormatTools.createLength(physicalSizeX, UNITS.MICROM), 0);
    }
    if (physicalSizeY != null) {
      store.setPixelsPhysicalSizeY(FormatTools.createLength(physicalSizeY, UNITS.MICROM), 0);
    }
  }

  class SCNHandler extends BaseHandler {

    // -- Fields --

    private String key;

    // -- DefaultHandler API methods --

    public void endElement(String uri, String localName, String qName) {
      key = null;
    }

    public void characters(char[] ch, int start, int length) {
      String value = new String(ch, start, length);
      addGlobalMeta(key, value);

      if ("endian".equals(key)) {
        core.get(0).littleEndian = value.equals("little");
      }
      else if ("channel_count".equals(key)) {
        core.get(0).sizeC = Integer.parseInt(value);
      }
      else if ("application_gain".equals(key)) {
        gain = new Double(value);
      }
      else if ("exposure_time".equals(key)) {
        exposureTime = new Double(value);
      }
      else if ("name".equals(key)) {
        imageName = value;
      }
    }

    public void startElement(String uri, String localName, String qName,
      Attributes attributes)
    {
      key = qName;

      for (int i=0; i<attributes.getLength(); i++) {
        String attrKey = attributes.getQName(i);
        String attrValue = attributes.getValue(i);
        addGlobalMeta(key + " " + attrKey, attrValue);

        if (key.equals("size_pix")) {
          if (attrKey.equals("width")) {
            core.get(0).sizeX = Integer.parseInt(attrValue);
          }
          else if (attrKey.equals("height")) {
            core.get(0).sizeY = Integer.parseInt(attrValue);
          }
        }
        else if (key.equals("scanner")) {
          if (attrKey.equals("max_value")) {
            long value = Long.parseLong(attrValue);
            if (value <= 256) {
              core.get(0).pixelType = FormatTools.UINT8;
            }
            else if (value <= 65535) {
              core.get(0).pixelType = FormatTools.UINT16;
            }
          }
        }
        else if (key.equals("size_mm")) {
          if (attrKey.equals("width")) {
            physicalSizeX = new Double(attrValue) / getSizeX();
            physicalSizeX *= 1000; // convert from mm to um
          }
          else if (attrKey.equals("height")) {
            physicalSizeY = new Double(attrValue) / getSizeY();
            physicalSizeY *= 1000; // convert from mm to um
          }
        }
        else if (key.equals("serial_number")) {
          if (attrKey.equals("value")) {
            serialNumber = attrValue;
          }
        }
        else if (key.equals("binning")) {
          if (attrKey.equals("value")) {
            binning = attrValue;
          }
        }
        else if (key.equals("image_date")) {
          if (attrKey.equals("value")) {
            acquisitionDate = attrValue;
          }
        }
        else if (key.equals("imager")) {
          if (attrKey.equals("value")) {
            model = attrValue;
          }
        }
      }
    }

  }

}
