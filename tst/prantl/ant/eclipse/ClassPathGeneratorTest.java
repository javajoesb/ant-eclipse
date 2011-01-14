package prantl.ant.eclipse;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class ClassPathGeneratorTest {

  private ClassPathGenerator classPathGenerator;

  @Before
  public void setUp() {
    classPathGenerator = new ClassPathGenerator(new EclipseTask());
  }

  @Test
  public void testExtractPath() {
    assertEquals("Null should return blank (something)", "", classPathGenerator.extractPath(null));
    assertEquals("Empty should return blank (spaces are not preserved)", "", classPathGenerator.extractPath("  "));
    assertEquals("Empty should return blank", "/", classPathGenerator.extractPath("/"));
    assertEquals("File should return blank", "", classPathGenerator.extractPath("file"));
    assertEquals( "/", classPathGenerator.extractPath("/file"));
    assertEquals("File.ext should return blank", "", classPathGenerator.extractPath("file.ext"));
    assertEquals("path and file", "path/", classPathGenerator.extractPath("path/File.ext"));
    assertEquals("path, path and file", "path/path/", classPathGenerator.extractPath("path/path/File.ext"));
    assertEquals("/path, path, file", "/path/path/", classPathGenerator.extractPath("/path/path/File.ext"));
    assertEquals("path/path/path/", classPathGenerator.extractPath("path/path/path/File.ext"));
  }
}
