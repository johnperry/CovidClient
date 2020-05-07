package org.covid;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import org.rsna.ctp.objects.DicomObject;
import org.rsna.util.FileUtil;
import org.rsna.util.XmlUtil;
import org.rsna.ui.RowLayout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A JPanel that provides a user interface for preparing a submission
 * directory set and the relevant metadata file(s).
 */
public class MetadataPanel extends JPanel implements ActionListener {

	private HeaderPanel headerPanel;
	private CenterPanel centerPanel;
	private FooterPanel footerPanel;
	Color background;
	File currentSelection = null;
	JFileChooser chooser;
	DirectoryFilter dirsOnly = new DirectoryFilter();
	
	Font sectionFont = new Font( "SansSerif", Font.BOLD, 16 );
	Font itemFont = new Font( "SansSerif", Font.PLAIN, 16 );
	Font mono = new Font( "Monospaced", Font.BOLD, 16 );
	
	String[] yesNo = {"", "Yes", "No"};
	String[] yesNoUnspecified = {"Yes", "No", "???"};
	String[] outcomes = {"Discharge", "Death", "???"};
	
	/**
	 * Class SubmissionPanel.
	 */
    public MetadataPanel() {
		super();
		Configuration config = Configuration.getInstance();
		setLayout(new BorderLayout());
		background = config.background;
		setBackground(background);
		centerPanel = new CenterPanel();
		footerPanel = new FooterPanel();
		footerPanel.select.addActionListener(this);
		footerPanel.save.addActionListener(this);
		headerPanel = new HeaderPanel();
		add(headerPanel, BorderLayout.NORTH);
		JScrollPane sp = new JScrollPane();
		sp.setViewportView(centerPanel);
		sp.getVerticalScrollBar().setBlockIncrement(50);
		sp.getVerticalScrollBar().setUnitIncrement(15);
		add(sp, BorderLayout.CENTER);
		add(footerPanel, BorderLayout.SOUTH);
	}
	
	/**
	 * Implementation of the ActionListener for the Save Changes button.
	 * @param event the event.
	 */
    public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(footerPanel.select)) {
			File selection = getSelection();
			if (selection != null) {
				currentSelection = selection;
				headerPanel.panelTitle.setText("Create Submission Metadata for "+currentSelection.getName());
				processSelection();
			}
		}
		else if (source.equals(footerPanel.save) && (currentSelection != null)) {
			centerPanel.saveMetadataXML(currentSelection);
		}
	}
	
	private File getSelection() {
		chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File dir = Configuration.getInstance().getStorageDir();
		File[] dirs = dir.listFiles(dirsOnly);
		if (dirs.length > 0) chooser.setSelectedFile(dirs[0]);
		else chooser.setSelectedFile(dir);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile();
		}
		else return null;
	}
	
	private void processSelection() {
		try {
			centerPanel.removeAll();
			File patient = currentSelection;
		
			int studyNumber = 1;
			int seriesNumber = 1;
			String patientSex = "";
			String patientAge = "";
			DicomObject dob = getFirstDicomObject(patient);
			if (dob != null) {
				patientSex = dob.getElementValue("PatientSex");
				patientAge = dob.getElementValue("PatientAge");
			}

			String pn = patient.getName();
			PatientIndexEntry ie = Index.getInstance().getInvEntry(pn);
			String ies = (ie != null) ? ie.toString() : null;
			String phiPatientID = (ie != null) ? ie.id : "";
			centerPanel.addSectionRow("Patient", ies, 1);

			centerPanel.addItemRow("PatientID", patient.getName(), 2);
			ItemValue ptage = centerPanel.addItemRow("PatientAge", patientAge, 2);
			ItemValue ptsex = centerPanel.addItemRow("PatientSex", patientSex, 2);
			centerPanel.addRadioPanelRow("CurrentSmoker", yesNoUnspecified, 2);
			centerPanel.addRadioPanelRow("FormerSmoker", yesNoUnspecified, 2);
			centerPanel.addRadioPanelRow("HeartDisease", yesNoUnspecified, 2);
			centerPanel.addRadioPanelRow("RespiratoryDisease", yesNoUnspecified, 2);
			centerPanel.addRadioPanelRow("Diabetes", yesNoUnspecified, 2);
			
			insertSymptoms();
			
			insertLabValues();
			
			File[] studies = patient.listFiles(dirsOnly);
			for (File study : studies) {
				String phi = getStudyPHI(phiPatientID, study);
				centerPanel.addSectionRow("ImagingProcedure", phi, 2);
				centerPanel.addItemFieldRow("DaysAfterOnset", "", 3);
				File[] series = study.listFiles(dirsOnly);
				int nImages = 0;
				String modality = "";
				boolean gotModality = false;
				for (File ser: series) {
					File[] images = ser.listFiles();
					nImages += images.length;
					if (!gotModality && (images.length > 0)) {
						try {
							DicomObject d = new DicomObject(images[0]);
							modality = d.getModality();
							gotModality = true;
						}
						catch (Exception tryAgain) { }
					}
				}
				centerPanel.addItemRow("Modality", modality, 3);
				centerPanel.addItemRow("NSeries", Integer.toString(series.length), 3);
				centerPanel.addItemRow("NImages", Integer.toString(nImages), 3);
				centerPanel.addRadioPanelRow("RUL", yesNoUnspecified, 3);
				centerPanel.addRadioPanelRow("RML", yesNoUnspecified, 3);
				centerPanel.addRadioPanelRow("RLL", yesNoUnspecified, 3);
				centerPanel.addRadioPanelRow("LUL", yesNoUnspecified, 3);
				centerPanel.addRadioPanelRow("LLL", yesNoUnspecified, 3);
			}
			
			insertTreatment();
			
			centerPanel.addSectionRow("Outcome", 2);
			centerPanel.addItemFieldRow("DaysAfterOnset", "", 3);
			centerPanel.addItemFieldRow("DaysInHospital", "", 3);
			centerPanel.addRadioPanelRow("Result", outcomes, 3);
		}
		catch (Exception ex) { ex.printStackTrace(); }
		centerPanel.revalidate();
	}
	
	private String getStudyPHI(String phiPatientID, File dir) {
		Study[] indexedStudies = Index.getInstance().listStudiesFor(phiPatientID);
		try {
			DicomObject dob = getFirstDicomObject(dir);
			String studyDate = dob.getStudyDate();
			String accession = dob.getAccessionNumber();
			for (Study study : indexedStudies) {
				if (study.anonAccession.equals(accession)
						&& study.anonDate.equals(studyDate)) {
					studyDate = study.phiDate;
					accession = study.phiAccession;
					studyDate = studyDate.substring(0,4) + "." +
								studyDate.substring(4,6) + "." +
								studyDate.substring(6,8);
					return studyDate + "/" + accession;
				}
			}
		}
		catch (Exception ex) { }
		return "";
	}
	
	private DicomObject getFirstDicomObject(File dir) {
		DicomObject dob;
		File[] files = dir.listFiles();
		for (File file : files) {
			if (file.isFile()) {
				try { 
					dob = new DicomObject(file);
					return dob;
				}
				catch (Exception notDICOM) { }
			}
		}
		for (File file : files) {
			if (file.isDirectory()) {
				return getFirstDicomObject(file);
			}
		}
		return null;
	}		
	
	private void insertSymptoms() {
		centerPanel.addSectionRow("Symptoms", 2);
		centerPanel.addItemFieldRow("DaysAfterOnset", "", 3);
		centerPanel.addRadioPanelRow("Cough", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("ShortnessOfBreath", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("ChestPain", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("MuscleAches", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("Diarrhea", yesNoUnspecified, 3);
		centerPanel.addItemFieldRowWithUnits("Temperature", "", "°C", 3);
		centerPanel.addItemFieldRowWithUnits("O2Sat", "", "%", 3);
		centerPanel.addButtonRow("Add New Symptoms Block");
	}
	private void insertSymptoms(LinkedList<Component>list) {
		centerPanel.addSectionRow(list, "Symptoms", 2);
		centerPanel.addItemFieldRow(list, "DaysAfterOnset", "", 3);
		centerPanel.addRadioPanelRow(list, "Cough", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "ShortnessOfBreath", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "ChestPain", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "MuscleAches", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "Diarrhea", yesNoUnspecified, 3);
		centerPanel.addItemFieldRowWithUnits(list, "Temperature", "", "°C", 3);
		centerPanel.addItemFieldRowWithUnits(list, "O2Sat", "", "%", 3);
		centerPanel.addButtonRow(list, "Add New Symptoms Block");
	}
	
	private void insertLabValues() {
		centerPanel.addSectionRow("LabValues", 2);
		centerPanel.addItemFieldRow("DaysAfterOnset", "", 3);
		centerPanel.addItemFieldRowWithUnits("CRP", "", "mg/L", 3);
		centerPanel.addItemFieldRowWithUnits("LDH", "", "U/L", 3);
		centerPanel.addItemFieldRowWithUnits("LymphocyteCount", "", "ul", 3);
		centerPanel.addItemFieldRowWithUnits("Hg", "", "g/dl", 3);
		centerPanel.addItemFieldRowWithUnits("D-dimer", "", "ng/mL", 3);
		centerPanel.addItemFieldRowWithUnits("Albumin", "", "g/dL", 3);
		centerPanel.addItemFieldRowWithUnits("DirectBilirubin", "", "mg/dL", 3);
		centerPanel.addItemFieldRowWithUnits("ALT", "", "IU/L", 3);
		centerPanel.addItemFieldRowWithUnits("IL6", "", "pg/mL", 3);
		centerPanel.addItemFieldRowWithUnits("Ferritin", "", "ng/mL", 3);
		centerPanel.addButtonRow("Add New Lab Values Block");
	}
	private void insertLabValues(LinkedList<Component>list) {
		centerPanel.addSectionRow(list, "LabValues", 2);
		centerPanel.addItemFieldRow(list, "DaysAfterOnset", "", 3);
		centerPanel.addItemFieldRowWithUnits(list, "CRP", "", "mg/L", 3);
		centerPanel.addItemFieldRowWithUnits(list, "LDH", "", "U/L", 3);
		centerPanel.addItemFieldRowWithUnits(list, "LymphocyteCount", "", "ul", 3);
		centerPanel.addItemFieldRowWithUnits(list, "Hg", "", "g/dl", 3);
		centerPanel.addItemFieldRowWithUnits(list, "D-dimer", "", "ng/mL", 3);
		centerPanel.addItemFieldRowWithUnits(list, "Albumin", "", "g/dL", 3);
		centerPanel.addItemFieldRowWithUnits(list, "DirectBilirubin", "", "mg/dL", 3);
		centerPanel.addItemFieldRowWithUnits(list, "ALT", "", "IU/L", 3);
		centerPanel.addItemFieldRowWithUnits(list, "IL6", "", "pg/mL", 3);
		centerPanel.addItemFieldRowWithUnits(list, "Ferritin", "", "ng/mL", 3);
		centerPanel.addButtonRow(list, "Add New Lab Values Block");
	}
	
	private void insertTreatment() {
		centerPanel.addSectionRow("Treatment", 2);
		centerPanel.addItemFieldRow("DaysAfterOnset", "", 3);
		centerPanel.addRadioPanelRow("SupplementalO2", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("HighFlowNasalO2", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("NonInvasiveVentillation", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("MechanicalVentilation", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("Steroids", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("AntiIL6", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("PlasmaTherapy", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("Remdesivir", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow("IVImmunoglobulin", yesNoUnspecified, 3);
		centerPanel.addButtonRow("Add New Treatment Block");
	}
	private void insertTreatment(LinkedList<Component>list) {
		centerPanel.addSectionRow(list, "Treatment", 2);
		centerPanel.addItemFieldRow(list, "DaysAfterOnset", "", 3);
		centerPanel.addRadioPanelRow(list, "SupplementalO2", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "HighFlowNasalO2", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "NonInvasiveVentillation", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "MechanicalVentilation", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "Steroids", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "AntiIL6", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "PlasmaTherapy", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "Remdesivir", yesNoUnspecified, 3);
		centerPanel.addRadioPanelRow(list, "IVImmunoglobulin", yesNoUnspecified, 3);
		centerPanel.addButtonRow(list, "Add New Treatment Block");
	}
	
	private String getPath(File base, File dir) {
		File root = base.getAbsoluteFile().getParentFile();
		int rootPathLength = root.getAbsolutePath().length();
		String path = dir.getAbsolutePath().substring(rootPathLength);
		return "." +path.replace("\\", "/");
	}			

	class DirectoryFilter implements FileFilter {
		public boolean accept(File f) {
			return f.isDirectory();
		}
	}		
		
	class HeaderPanel extends Panel {
		public JLabel panelTitle;
		public HeaderPanel() {
			super();
			setBackground(background);
			Box box = Box.createVerticalBox();
			panelTitle = new JLabel("Create Submission Metadata");
			panelTitle.setFont( new Font( "SansSerif", Font.BOLD, 18 ) );
			box.add(Box.createVerticalStrut(10));
			panelTitle.setForeground(Color.BLUE);
			box.add(panelTitle);
			box.add(Box.createVerticalStrut(10));
			this.add(box);
		}		
	}
	
	class CenterPanel extends JPanel implements ActionListener {
		public CenterPanel() {
			super();
			setBackground(background);
			setLayout(new RowLayout());
		}
		
		public SectionLabel addSectionRow(String title, String value, int level) {
			SectionLabel c = new SectionLabel(title, level);
			add(c);
			if (value != null) add(new SectionValue(value));
			add(RowLayout.crlf());
			return c;
		}
		
		public SectionLabel addSectionRow(LinkedList<Component>list, String title, String value, int level) {
			SectionLabel c = new SectionLabel(title, level);
			list.add(c);
			if (value != null) list.add(new SectionValue(value));
			list.add(RowLayout.crlf());
			return c;
		}
		
		public SectionLabel addSectionRow(String title, int level) {
			return addSectionRow(title, null, level);
		}
		
		public SectionLabel addSectionRow(LinkedList<Component>list, String title, int level) {
			return addSectionRow(list, title, null, level);
		}
		
		public ItemValue addItemRow(String title, String text, int level) {
			add(new ItemLabel(title, level));
			ItemValue c = new ItemValue(text);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
		
		public ItemValue addItemRow(LinkedList<Component>list, String title, String text, int level) {
			list.add(new ItemLabel(title, level));
			ItemValue c = new ItemValue(text);
			list.add(c);
			list.add(RowLayout.crlf());
			return c;
		}
		
		public ItemField addItemFieldRow(String title, String text, int level) {
			add(new ItemLabel(title, level));
			ItemField c = new ItemField(text);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
			
		public ItemField addItemFieldRow(LinkedList<Component>list, String title, String text, int level) {
			list.add(new ItemLabel(title, level));
			ItemField c = new ItemField(text);
			list.add(c);
			list.add(RowLayout.crlf());
			return c;
		}
			
		public ItemFieldWithUnits addItemFieldRowWithUnits(String title, String text, String units, int level) {
			add(new ItemLabel(title, level));
			ItemFieldWithUnits c = new ItemFieldWithUnits(text, units);
			add(c);
			ItemValueUnits u = new ItemValueUnits(units);
			add(u);
			add(RowLayout.crlf());
			return c;
		}
			
		public ItemFieldWithUnits addItemFieldRowWithUnits(LinkedList<Component>list, String title, String text, String units, int level) {
			list.add(new ItemLabel(title, level));
			ItemFieldWithUnits c = new ItemFieldWithUnits(text, units);
			list.add(c);
			ItemValueUnits u = new ItemValueUnits(units);
			list.add(u);
			list.add(RowLayout.crlf());
			return c;
		}
			
		public ItemComboBox addItemComboBoxRow(String title, String[] text, int level) {
			add(new ItemLabel(title, level));
			ItemComboBox c = new ItemComboBox(text, 0);
			add(c);
			add(RowLayout.crlf());
			return c;
		}
		
		public ItemComboBox addItemComboBoxRow(LinkedList<Component>list, String title, String[] text, int level) {
			list.add(new ItemLabel(title, level));
			ItemComboBox c = new ItemComboBox(text, 0);
			list.add(c);
			list.add(RowLayout.crlf());
			return c;
		}
		
		public RadioPanel addRadioPanelRow(String title, String[] options, int level) {
			add(new ItemLabel(title, level));
			RadioPanel rp = new RadioPanel(options);
			add(rp);
			add(RowLayout.crlf());
			return rp;
		}		
		
		public RadioPanel addRadioPanelRow(LinkedList<Component>list, String title, String[] options, int level) {
			list.add(new ItemLabel(title, level));
			RadioPanel rp = new RadioPanel(options);
			list.add(rp);
			list.add(RowLayout.crlf());
			return rp;
		}		
		
		public AddButton addButtonRow(String text) {
			add(Box.createHorizontalStrut(10));
			AddButton ab = new AddButton(text);
			ab.addActionListener(this);
			add(ab);
			add(RowLayout.crlf());
			return ab;
		}		
		
		public AddButton addButtonRow(LinkedList<Component>list, String text) {
			list.add(Box.createHorizontalStrut(10));
			AddButton ab = new AddButton(text);
			ab.addActionListener(this);
			list.add(ab);
			list.add(RowLayout.crlf());
			return ab;
		}		
		
		public void actionPerformed(ActionEvent event) {
			Object source = event.getSource();
			Component[] comps = getComponents();
			LinkedList<Component> list = new LinkedList<Component>();
			for (int i=0; i<comps.length; i++) {
				list.add(comps[i]);
				if (comps[i].equals(source)) {
					AddButton button = (AddButton)list.removeLast(); //remove the button
					list.removeLast();	//remove the strut from before the button
					String buttonText = button.getText();
					
					if (buttonText.contains("Lab")) {
						insertLabValues(list);
					}
					else if (buttonText.contains("Symptoms")) {
						insertSymptoms(list);
					}
					else if (buttonText.contains("Treatment")) {
						insertTreatment(list);
					}
					list.removeLast(); //remove the extra CRLF
				}
			}
			removeAll();
			for (Component c : list) add(c);
			revalidate();
		}
		
		public void saveMetadataXML(File dir) {
			try {
				Document doc = XmlUtil.getDocument();
				Element root = doc.createElement(dir.getName());
				doc.appendChild(root);
				
				Element parent = root;
				Element lastPatient = null;
				Element lastStudy = null;
				Element lastSeries = null;
				Element lastNodule = null;
				Component[] comps = getComponents();
				for (int i=0; i<comps.length; i++) {
					Component c = comps[i];
					if (c instanceof SectionLabel) {
						SectionLabel lbl = (SectionLabel)c;
						String text = lbl.getText();
						Element e = doc.createElement(text);
						if (text.equals("Patient")) {
							parent = root;
							lastPatient = e;
						}
						else {
							parent = lastPatient;
						}
						parent.appendChild(e);
						parent = e;
					}
					else if (c instanceof ItemLabel) {
						ItemLabel lbl = (ItemLabel)c;
						String text = lbl.getText();
						Element e = doc.createElement(text);
						parent.appendChild(e);
						parent = e;
					}
					else if (c instanceof ItemValue) {
						ItemValue val = (ItemValue)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof ItemField) {
						ItemField val = (ItemField)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof ItemFieldWithUnits) {
						ItemFieldWithUnits val = (ItemFieldWithUnits)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent.setAttribute("units", val.units);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof ItemComboBox) {
						ItemComboBox val = (ItemComboBox)c;
						String text = (String)val.getSelectedItem();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
					else if (c instanceof RadioPanel) {
						RadioPanel val = (RadioPanel)c;
						String text = val.getText();
						parent.setTextContent(text);
						parent =(Element)parent.getParentNode();
					}
				}
				File metadata = new File(dir, "metadata.xml");
				FileUtil.setText(metadata, XmlUtil.toPrettyString(doc));
				//delete the ..export directory to indicate that metadata has changed since export
				File hidden = new File(dir, "..export");
				hidden.delete();
			}
			catch (Exception ex) { ex.printStackTrace(); }
		}
	}

	class FooterPanel extends JPanel {
		public JButton select;
		public JButton save;
		public FooterPanel() {
			super();
			setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
			setLayout(new FlowLayout());
			setBackground(background);
			select = new JButton("Select Patient");
			save = new JButton("Save Metadata");
			add(select);
			add(Box.createHorizontalStrut(15));
			add(save);
		}
	}
	
	class SectionLabel extends JLabel	{
		public SectionLabel(String text, int level) {
			super(text);
			setFont(sectionFont);
			setForeground(Color.BLUE);
			setBorder(new EmptyBorder(0, 20*level, 0, 0));
		}
	}
	
	//class for phi in Patient element
	class SectionValue extends JLabel {
		public SectionValue(String text) {
			super(text);
			setFont(mono);
			setForeground(Color.BLACK);
		}
	}
	
	class ItemLabel extends JLabel {
		public ItemLabel(String text, int level) {
			super(text);
			setFont(itemFont);
			setForeground(Color.BLACK);
			setBorder(new EmptyBorder(0, 20*level, 0, 0));
		}
	}
	
	class ItemValue extends JLabel {
		public ItemValue(String text) {
			super(text);
			setFont(mono);
			setForeground(Color.BLACK);
		}
	}
	
	class ItemValueUnits extends JLabel {
		public ItemValueUnits(String text) {
			super(text);
			setFont(mono);
			setForeground(Color.BLACK);
		}
	}
	
	class ItemField extends JTextField {
		public ItemField(String text) {
			super("", 20);
			setFont(mono);
			setForeground(Color.BLACK);
			setAlignmentX(0.0f);
			setColumns(25);
			setHorizontalAlignment(JTextField.RIGHT);
		}
	}
	
	class ItemFieldWithUnits extends JTextField {
		String units;
		public ItemFieldWithUnits(String text, String units) {
			super("", 20);
			this.units = units;
			setFont(mono);
			setForeground(Color.BLACK);
			setAlignmentX(0.0f);
			setColumns(25);
			setHorizontalAlignment(JTextField.RIGHT);
		}
	}
	
	class ItemComboBox extends JComboBox<String> {
		public ItemComboBox(String[] values, int selectedIndex) {
			super(values);
			setSelectedIndex(selectedIndex);
			setFont(mono);
			setBackground(Color.white);
			setEditable(false);
			setAlignmentX(0.0f);
			Dimension d = getPreferredSize();
			d.width = 250;
			setPreferredSize(d);
		}
	}
	
	class AddButton extends JButton {
		public AddButton(String text) {
			super(text);
		}
	}
	
	class RadioPanel extends JPanel {
		String[] options;
		ButtonGroup group;
		public RadioPanel(String[] options) {
			super();
			setLayout(new FlowLayout(FlowLayout.LEADING, 5, 0));
			setBackground(background);
			this.options = options;
			group = new ButtonGroup();
			JRadioButton b = null;
			for (String s : options) {
				b = new JRadioButton(s);
				b.setBackground(background);
				this.add(b);
				group.add(b);
			}
			if (b != null) b.setSelected(true);
			Dimension d = getPreferredSize();
			d.width = 250;
			setPreferredSize(d);
		}
		public String getText() {
			Enumeration<AbstractButton> e = group.getElements();
			while (e.hasMoreElements()) {
				AbstractButton b = e.nextElement();
				if (b.isSelected()) {
					String value = b.getText().toLowerCase();
					if (value.equals("???")) value = "";
					return value;
				}
			}
			return "";
		}
	}
	
}