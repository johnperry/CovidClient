package org.covid;

/**
 * The Anonymizer program installer, consisting of just a
 * main method that instantiates a SimpleInstaller.
 */
public class Installer {

	static String windowTitle = "CovidClient Installer";
	static String programName = "CovidClient";
	static String introString = "<p><b>CovidClient</b> is a stand-alone tool for importing, de-identifying, "
								+ "and organizing DICOM objects for submission to the Covid-19 dataset.</p>";

	public static void main(String args[]) {
		new SimpleInstaller(windowTitle,programName,introString);
	}
}
