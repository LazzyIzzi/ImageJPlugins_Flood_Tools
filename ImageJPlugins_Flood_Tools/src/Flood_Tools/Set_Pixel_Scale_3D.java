package Flood_Tools;

import java.awt.Color;
import java.awt.Font;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;

public class Set_Pixel_Scale_3D implements PlugIn
{
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff

	@Override
	public void run(String arg)
	{
		ImagePlus imp = IJ.getImage();
		Calibration cal = imp.getCalibration();
		
		//Calibration cal = new Calibration();
		cal = imp.getCalibration();
		
		GenericDialog gd = new GenericDialog("Set Stack Pixel Size");
		gd.addNumericField("Horizontal (x) Size:", cal.pixelWidth);
		gd.addStringField("Horizontal (x) unit:", cal.getXUnit());
		gd.addNumericField("Vertical (y) Size:", cal.pixelHeight);
		gd.addStringField("Vertical (y) unit:", cal.getYUnit());
		gd.addNumericField("Depth (z) Size:", cal.pixelDepth);
		gd.addStringField("Depth (z) unit:", cal.getZUnit());
		gd.addCheckbox("Remove Scale", false);
		gd.setBackground(myColor);
		gd.showDialog();
		
		
		if(gd.wasOKed())
		{
			boolean clearScale = gd.getNextBoolean();
			if(clearScale)
			{
				cal.pixelWidth = 1.0;
				cal.pixelHeight = 1.0;
				cal.pixelDepth = 1.0;
				cal.setXUnit("pixel");
				cal.setYUnit("pixel");
				cal.setZUnit("pixel");
				
			}
			else
			{
			cal.pixelWidth = gd.getNextNumber();
			cal.pixelHeight = gd.getNextNumber();
			cal.pixelDepth = gd.getNextNumber();
			cal.setXUnit(gd.getNextString());
			cal.setYUnit(gd.getNextString());
			cal.setZUnit(gd.getNextString());
			}
		}
		
		imp.updateAndRepaintWindow();		
	}

}
