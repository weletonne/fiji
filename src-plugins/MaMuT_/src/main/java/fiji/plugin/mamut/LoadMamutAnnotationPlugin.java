package fiji.plugin.mamut;

import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.io.IOUtils;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

public class LoadMamutAnnotationPlugin implements PlugIn {

	private static File file;

	@Override
	public void run(String arg0) {
		
		Logger logger = Logger.IJ_LOGGER;
		if (null == file) {
			File folder = new File(System.getProperty("user.dir")).getParentFile().getParentFile();
			file = new File(folder.getPath() + File.separator + "data.xml");
		}
		file = IOUtils.askForFileForLoading(file, "Open a MaMuT xml file", IJ.getInstance(), logger );
		if (null == file) {
			return;
		}
		
		MaMuT mamut = new MaMuT();
		try {
			mamut.load(file);
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public static void main(String[] args) {
		ImageJ.main(args);
		
		LoadMamutAnnotationPlugin plugin = new LoadMamutAnnotationPlugin();
		plugin.run(null);
	}

}