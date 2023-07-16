package Flood_Tools;

import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import jhd.ImageJAddins.GenericDialogAddin;
import jhd.ImageJAddins.GenericDialogAddin.*;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.gui.HistogramPlot;

/**Plugin the line the resolved pores with unresolved porosity
 * @author John
 *
 */
public class Add_Pore_Liner implements PlugInFilter, DialogListener
{

	ImagePlus hybridPhiImp;//add pore lining  0<porosity<1  to this hybrid porosity stack 
	NumericField maxPhiNF,minPhiNF,thicknessNF;
	
	double maxPhi = 0.5;
	double minPhi = 0.2;
	double thickness = 2;
	boolean createNew = true;
	
	Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
	final Color myColor = new Color(240,230,190);//slightly darker than buff

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.hybridPhiImp = imp;
		return DOES_32+STACK_REQUIRED+NO_UNDO;
	}
	Random rand = new Random();

	//**********************************************************************************************
	@Override
	public void run(ImageProcessor ip)
	{		
		GenericDialog gd = new GenericDialog("Add Random Pore-Lining Porosity");
		GenericDialogAddin gda = new GenericDialogAddin();
		gd.addMessage("Add random pore-lining unresolved (0<phi<1) porosity",myFont);
		gd.addNumericField("Minimum porosity:",minPhi);
		minPhiNF = gda.getNumericField(gd, null, "minPhi");
		gd.addNumericField("Maximum porosity:",maxPhi);
		maxPhiNF = gda.getNumericField(gd, null, "maxPhi");
		gd.addNumericField("Pore lining Thickness "+hybridPhiImp.getCalibration().getUnit(), thickness);
		thicknessNF = gda.getNumericField(gd, null, "thickness");
		gd.addCheckbox("Create New Image",createNew);
		gd.addDialogListener(this);
		gd.addHelp("https://lazzyizzi.github.io/DistanceMapPages/AddPoreLiner.html");
		gd.setBackground(myColor);
		gd.showDialog();
		
		if(gd.wasCanceled()) return;

		minPhi = gd.getNextNumber();
		maxPhi = gd.getNextNumber();
		thickness = gd.getNextNumber();
		createNew = gd.getNextBoolean();

		String mapType = hybridPhiImp.getProp("MapType");
		if(mapType.equals("HybridPorosity"))
		{
			if(createNew)
			{
				String title = hybridPhiImp.getTitle();
				int loc = title.lastIndexOf(".");
				title = title.substring(0, loc) + "_Lining"+title.substring(loc);
				hybridPhiImp = hybridPhiImp.duplicate();
				hybridPhiImp.setTitle(title);
				hybridPhiImp.show();
			}
			int depth = hybridPhiImp.getStackSize();
			
			Object[] oPhiData = hybridPhiImp.getStack().getImageArray();
			
			float[] phiSlice;

			Random rand = new Random();
			float slope = (float)(maxPhi-minPhi);
			float intercept =  (float)minPhi;

			for(int i=0;i<depth;i++)
			{
				IJ.showProgress((double)i/(double)depth);
				phiSlice = (float[])oPhiData[i];
				for(int j=0;j<phiSlice.length;j++)
				{
					if(phiSlice[j] >0 && phiSlice[j]<thickness)
					{
						phiSlice[j] = slope*rand.nextFloat()+intercept;	
					}
				}				
			}
			IJ.run(hybridPhiImp, "Porosity To Hybrid", " ");
		}
	}

	//**********************************************************************************************
	@Override
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		boolean dialogOK = true;
		
		if(e!=null)
		{
			double maxPhi = maxPhiNF.getNumber();
			double minPhi = minPhiNF.getNumber();
			double thickness = thicknessNF.getNumber();
			Object src = e.getSource();
			if(src instanceof TextField)
			{
				TextField tf = (TextField)src;
				String name = tf.getName();
				switch(name)
				{
				case "maxPhi":
					if(Double.isNaN(maxPhi) || maxPhi < minPhi || maxPhi < 0 || maxPhi >1 )
					{
						maxPhiNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						maxPhiNF.getNumericField().setBackground(Color.white);
						dialogOK = true;
					}
					break;
				case "minPhi":
					if(Double.isNaN(minPhi) || minPhi <=0  || minPhi >1 || minPhi > maxPhi)
					{
						minPhiNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						minPhiNF.getNumericField().setBackground(Color.white);
						dialogOK = true;
					}
					break;
				case "thickness":
					if(Double.isNaN(thickness) || thickness <=0  )
					{
						thicknessNF.getNumericField().setBackground(Color.red);
						dialogOK = false;
					}
					else
					{
						thicknessNF.getNumericField().setBackground(Color.white);
						dialogOK = true;
					}
					break;
				}				
			}
		}
		return dialogOK;
	}
}
