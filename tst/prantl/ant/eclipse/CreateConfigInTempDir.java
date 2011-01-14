package prantl.ant.eclipse;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import org.apache.tools.ant.Project;

public class CreateConfigInTempDir extends TestCase
{

    private File outDir;
    private EclipseTask task;
    private Project project;

    @Override
    public void setUp()
    {
        task = new EclipseTask();
        project = new Project();
        task.setProject(project);
        task.setUpdateAlways(true);

        outDir = new File(System.getProperty("java.io.tmpdir"), "eclipseTaskTest");
        if(!outDir.exists())
        {
            assertTrue(String.format("Failed to crate temp test dir %s", outDir.getAbsolutePath()), outDir.mkdirs());
        }
        File antFile = new File(outDir, "build.xml");
        if(!antFile.exists())
        {
            boolean isCreated = false;

            try
            {
                isCreated = antFile.createNewFile();
            }
            catch(IOException e)
            {
                e.printStackTrace();
                fail(e.getLocalizedMessage());
            }
            assertTrue(String.format("We should have created %s", antFile.getAbsolutePath()), isCreated);
        }
        project.setProperty(FileEclipseOutput.ANT_FILE_PROPERTY, antFile.getAbsolutePath());
        project.setBaseDir(outDir);

    }

    public void testWriteFileInTemp()
    {
        task.setDestDir(outDir);
        ClassPathElement classPath = task.createClassPath();
        ClassPathEntryLibraryElement library = classPath.createLibrary();
        library.setPath("lib/path1");
        library.setSource("lib/source1");
        
        library = classPath.createLibrary();
        library.setPath("lib/path2");
        library.setSource("lib/source2");
        
        ClassPathEntrySourceElement source = classPath.createSource();
        source.setOutput("webroot/output");
        source.setPath("webroot/path");
        task.execute();
    }
}
