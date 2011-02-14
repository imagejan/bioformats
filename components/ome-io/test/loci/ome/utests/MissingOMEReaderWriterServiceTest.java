//
// MissingOMEReaderWriterServiceTest.java
//

/*
OME database I/O package for communicating with OME and OMERO servers.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden and Philip Huettl.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.ome.utests;

import static org.testng.AssertJUnit.assertNotNull;

import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.ome.io.services.OMEReaderWriterService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/ome-io/test/loci/ome/utests/MissingOMEReaderWriterServiceTest.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/ome-io/test/loci/ome/utests/MissingOMEReaderWriterServiceTest.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author callan
 */
public class MissingOMEReaderWriterServiceTest {

  private ServiceFactory sf;

  @BeforeMethod
  public void setUp() throws DependencyException {
    sf = new ServiceFactory();
  }

  @Test(expectedExceptions={DependencyException.class})
  public void testInstantiate() throws DependencyException {
    OMEReaderWriterService service = 
      sf.getInstance(OMEReaderWriterService.class);
    assertNotNull(service);
  }

}
