package org.covid;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import org.apache.log4j.*;
import org.rsna.ctp.stdstages.anonymizer.dicom.DAScript;
import org.rsna.ui.ApplicationProperties;
import org.rsna.ui.SourcePanel;
import org.rsna.util.BrowserUtil;
import org.rsna.util.FileUtil;
import org.rsna.util.HttpUtil;
import org.rsna.util.ImageIOTools;
import org.rsna.util.JarUtil;
import org.rsna.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The Anonymizer program base class.
 */
public class CovidClient extends JFrame {

    private String					windowTitle = "CovidClient - v5";
    private JPanel					splitPanel;
    private WelcomePanel			welcomePanel;
    private SCUPanel				scuPanel;
    private SCPPanel				scpPanel;
    private SourcePanel				sourcePanel;
    private RightPanel				rightPanel;
    private Viewer 					viewerPanel;
    private Editor 					editorPanel;
    private FilterPanel				filterPanel;
    private AnonymizerPanel			anonymizerPanel;
    private MetadataPanel			metadataPanel;
    private ExportPanel				exportPanel;
    private IndexPanel				indexPanel;
    private HtmlJPanel 				helpPanel;
    private LogPanel				logPanel;
    private MainPanel				mainPanel;
    private ImportPanel				importPanel;
    private AdminPanel				adminPanel;

	static Logger logger;

	/**
	 * The main method to start the program.
	 * @param args the list of arguments from the command line.
	 */
    public static void main(String args[]) {
		new CovidClient();
    }

	/**
	 * Class constructor; creates the program main class.
	 */
    public CovidClient() {
		super();
		
		//Initialize Log4J
		File logs = new File("logs");
		logs.mkdirs();
		for (File f : logs.listFiles()) FileUtil.deleteAll(f);
		File logProps = new File("log4j.properties");
		String propsPath = logProps.getAbsolutePath();
		if (!logProps.exists()) {
			System.out.println("Logger configuration file: "+propsPath);
			System.out.println("Logger configuration file not found.");
		}
		PropertyConfigurator.configure(propsPath);
		logger = Logger.getLogger(CovidClient.class);
		logPanel = LogPanel.getInstance();

		//Load the configuration singleton
		Configuration config = Configuration.getInstance();
		
		//Initialize the SITEID
		String propsSITEID = config.getProps().getProperty("SITEID");
		if (propsSITEID == null) {
			long t = System.currentTimeMillis()/60L;
			propsSITEID = Long.toString(t);
			propsSITEID = propsSITEID.substring(propsSITEID.length() - 6);
			config.getProps().setProperty("SITEID", propsSITEID);
			config.store();
		}
		File daScriptFile = new File(config.dicomScriptFile);
		DAScript daScript = DAScript.getInstance(daScriptFile);
		Document daDoc = daScript.toXML();
		Element daRoot = daDoc.getDocumentElement();
		Node child = daRoot.getFirstChild();
		Element e = null;
		while (child != null) {
			if (child.getNodeName().equals("p")) {
				e = (Element)child;
				if (e.getAttribute("t").equals("SITEID")) break;
			}
			child = child.getNextSibling();
		}
		String scriptSITEID = e.getTextContent().trim();
		if (!scriptSITEID.equals(propsSITEID)) {
			e.setTextContent(propsSITEID);
			FileUtil.setText(daScriptFile, daScript.toXMLString());
		}
		
		//Put the build date/time in the window title
		try {
			File program = new File("CovidClient.jar");
			String date = JarUtil.getManifestAttributes(program).get("Date");
			windowTitle += " - " + date;
		}
		catch (Exception ignore) { }
		setTitle(windowTitle);
		addWindowListener(new WindowCloser(this));
		mainPanel = new MainPanel();
		getContentPane().add(mainPanel, BorderLayout.CENTER);
		
		//Construct the UI
		welcomePanel = WelcomePanel.getInstance();
		scuPanel = SCUPanel.getInstance();
		scpPanel = SCPPanel.getInstance();
		sourcePanel = new SourcePanel(config.getProps(), "Directory", config.background);
		rightPanel = new RightPanel(sourcePanel);
		JSplitPane jSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, rightPanel);
		jSplitPane.setResizeWeight(0.5d);
		jSplitPane.setContinuousLayout(true);
		splitPanel = new JPanel(new BorderLayout());
		splitPanel.add(jSplitPane, BorderLayout.CENTER);
		
		anonymizerPanel = new AnonymizerPanel();
		viewerPanel = new Viewer();
		editorPanel = new Editor();
		filterPanel = FilterPanel.getInstance();
		metadataPanel = new MetadataPanel();
		exportPanel = ExportPanel.getInstance();
		indexPanel = new IndexPanel();
		helpPanel = new HtmlJPanel( FileUtil.getText( new File(config.helpfile) ) );
		
		importPanel = new ImportPanel();
		importPanel.addTabs(
			scuPanel,
			scpPanel,
			splitPanel);
		
		adminPanel = new AdminPanel();
		adminPanel.addTabs(
			viewerPanel,
			editorPanel,
			filterPanel,
			anonymizerPanel,
			indexPanel,
			logPanel);
		
		mainPanel.addTabs(
			welcomePanel,
			importPanel,
			metadataPanel,
			exportPanel,
			adminPanel,
			helpPanel);
		
		sourcePanel.addFileListener(viewerPanel);
		sourcePanel.addFileListener(editorPanel);
		pack();
		positionFrame();
		setVisible(true);
		System.out.println("Initialization complete");
		
		//Check the registration
		boolean requireRegistration = !config.getProps().getProperty("requireRegistration","yes").equals("no");
		if (requireRegistration && !isRegistered(propsSITEID)) {
			RegistrationDialog dialog = new RegistrationDialog(propsSITEID, "", "", "", "", "", "", "");
			boolean registered = false;
			while (dialog.show(this)) {
				String username = dialog.getParam("username");
				String email = dialog.getParam("email");
				String phone = dialog.getParam("phone");
				String sitename = dialog.getParam("sitename");
				String adrs1 = dialog.getParam("adrs1");
				String adrs2 = dialog.getParam("adrs2");
				String adrs3 = dialog.getParam("adrs3");
				String accept = dialog.getParam("accept");
				if (!sitename.equals("") && !username.equals("") && isValidEmail(email) && !accept.equals("")) {
					if (registered = register(propsSITEID, username, email, phone, sitename, adrs1, adrs2, adrs3)) {
						break;
					}
					else {
						JOptionPane.showMessageDialog(this,
									"The server refused the registration.",
									"Register",
									JOptionPane.ERROR_MESSAGE);
						exit();
					}
				}
				else {
					JOptionPane.showMessageDialog(this,
					 			"Please enter all fields and ensure\n"
					 			+"that the email address is valid.",
					 			"Register",
					 			JOptionPane.ERROR_MESSAGE);
				}
			}
			if (!registered) exit();
		}
    }
    
	private boolean isValidEmail(String email) {
      String regex = "^[\\w-_\\.+]*[\\w-_\\.]\\@([\\w]+\\.)+[\\w]+[\\w]$";
      return email.matches(regex);
	}
    
	class MainPanel extends JPanel implements ChangeListener {
		public JTabbedPane tabbedPane;
		public MainPanel() {
			super();
			this.setLayout(new BorderLayout());
			setBackground(Configuration.getInstance().background);
			tabbedPane = new JTabbedPane();
			this.add(tabbedPane,BorderLayout.CENTER);
		}
		public void addTabs(
						 WelcomePanel wp,
						 ImportPanel imp,
						 MetadataPanel metadata,
						 ExportPanel export,
						 AdminPanel admin,
						 HtmlJPanel help) {
			tabbedPane.addTab("Welcome", wp);
			tabbedPane.addTab("<html><center>DICOM<br>Deidentification</center></html>", imp);
			tabbedPane.addTab("<html><center>Metadata<br>Entry</center></html>", metadata);
			tabbedPane.addTab("<html><center>Submission<br>Upload</center></html>", export);
			tabbedPane.addTab("Administration", admin);
			tabbedPane.addTab("Help", help);
			tabbedPane.addChangeListener(this);
			tabbedPane.setSelectedIndex(0);
		}
		public void stateChanged(ChangeEvent event) {
			Component comp = tabbedPane.getSelectedComponent();
			if (comp.equals(scuPanel)) scuPanel.setFocus();
		}
	}
	
	class ImportPanel extends JPanel implements ChangeListener {
		public JTabbedPane tabbedPane;
		public ImportPanel() {
			super();
			this.setLayout(new BorderLayout());
			setBackground(Configuration.getInstance().background);
			tabbedPane = new JTabbedPane();
			this.add(tabbedPane,BorderLayout.CENTER);
		}
		public void addTabs(
						 SCUPanel scu,
						 SCPPanel scp,
						 JPanel source) {
			tabbedPane.addTab("Directory", source);
			tabbedPane.addTab("Q/R SCU", scu);
			tabbedPane.addTab("Storage SCP", scp);
			tabbedPane.addChangeListener(this);
			tabbedPane.setSelectedIndex(0);
		}
		public void stateChanged(ChangeEvent event) {
			Component comp = tabbedPane.getSelectedComponent();
			if (comp.equals(scuPanel)) scuPanel.setFocus();
		}
	}
	
	class AdminPanel extends JPanel implements ChangeListener {
		public JTabbedPane tabbedPane;
		public AdminPanel() {
			super();
			this.setLayout(new BorderLayout());
			setBackground(Configuration.getInstance().background);
			tabbedPane = new JTabbedPane();
			this.add(tabbedPane,BorderLayout.CENTER);
		}
		public void addTabs(
						 Viewer viewer,
						 Editor editor,
						 FilterPanel filter,
						 AnonymizerPanel script,
						 IndexPanel index,
						 LogPanel logPanel) {
			tabbedPane.addTab("Viewer", viewer);
			tabbedPane.addTab("Elements", editor);
			tabbedPane.addTab("Filter", filter);
			tabbedPane.addTab("Script", script);
			tabbedPane.addTab("Index", index);
			tabbedPane.addTab("Log", logPanel);
			tabbedPane.addChangeListener(viewer);
			tabbedPane.addChangeListener(this);
			tabbedPane.setSelectedIndex(4);
		}
		public void stateChanged(ChangeEvent event) {
			Component comp = tabbedPane.getSelectedComponent();
			if (comp.equals(indexPanel)) indexPanel.setFocus();
			else if (comp.equals(filterPanel)) filterPanel.setFocus();
			else if (comp.equals(logPanel)) logPanel.reload();
		}
	}
	
	private boolean isRegistered(String siteID) {
		try {
			String url = Configuration.getInstance().getProps().getProperty("regURL", "http://upload.open-qic.org:80");
			url += "/qicadmin/check?siteID="+siteID;
			HttpURLConnection conn = HttpUtil.getConnection(url);
			conn.setReadTimeout(120 * 1000);
			conn.setConnectTimeout(20 * 1000);
			conn.setRequestMethod("GET");
			conn.connect();
			int responseCode = conn.getResponseCode();
			return (responseCode == 200);
		}
		catch (Exception unable) { unable.printStackTrace(); return false; }
	}

	private boolean register(
				String siteID, 
				String username, 
				String email, 
				String phone, 
				String sitename, 
				String adrs1, 
				String adrs2, 
				String adrs3) {
		try {
			String url = Configuration.getInstance().getProps().getProperty("regURL", "http://upload.open-qic.org:80");
			url += "/qicadmin/create" 
				+ "?siteID="+siteID + "&username="+username + "&email="+email + "&phone="+phone 
				+ "&sitename="+sitename + "&adrs1="+adrs1 + "&adrs2="+adrs2 + "&adrs3="+adrs3 ;
			HttpURLConnection conn = HttpUtil.getConnection(url);
			conn.setReadTimeout(120 * 1000);
			conn.setConnectTimeout(20 * 1000);
			conn.setRequestMethod("GET");
			conn.connect();
			int responseCode = conn.getResponseCode();
			return (responseCode == 200);
		}
		catch (Exception unable) { return false; }
	}

	public void exit() {
		dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
	}
	
    class WindowCloser extends WindowAdapter {
		JFrame parent;
		public WindowCloser(JFrame parent) {
			this.parent = parent;
		}
		public void windowClosing(WindowEvent evt) {
			Configuration config = Configuration.getInstance();
			config.getIntegerTable().close();
			Index.getInstance().close();
			SCPPanel.getInstance().shutdown();
			Point p = getLocation();
			config.put("x", Integer.toString(p.x));
			config.put("y", Integer.toString(p.y));
			Toolkit t = getToolkit();
			Dimension d = parent.getSize ();
			config.put("w", Integer.toString(d.width));
			config.put("h", Integer.toString(d.height));
			config.put("subdirectories", (sourcePanel.getSubdirectories()?"yes":"no"));
			config.store();
			System.exit(0);
		}
    }

	private void positionFrame() {
		Configuration config = Configuration.getInstance();
		int x = StringUtil.getInt( config.get("x"), 0 );
		int y = StringUtil.getInt( config.get("y"), 0 );
		int w = StringUtil.getInt( config.get("w"), 0 );
		int h = StringUtil.getInt( config.get("h"), 0 );
		boolean noProps = ((w == 0) || (h == 0));
		int wmin = 800;
		int hmin = 600;
		if ((w < wmin) || (h < hmin)) {
			w = wmin;
			h = hmin;
		}
		if ( noProps || !screensCanShow(x, y) || !screensCanShow(x+w-1, y+h-1) ) {
			Toolkit t = getToolkit();
			Dimension scr = t.getScreenSize ();
			x = (scr.width - wmin)/2;
			y = (scr.height - hmin)/2;
			w = wmin;
			h = hmin;
		}
		setSize( w, h );
		setLocation( x, y );
	}

	private boolean screensCanShow(int x, int y) {
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screens = env.getScreenDevices();
		for (GraphicsDevice screen : screens) {
			GraphicsConfiguration[] configs = screen.getConfigurations();
			for (GraphicsConfiguration gc : configs) {
				if (gc.getBounds().contains(x, y)) return true;
			}
		}
		return false;
	}
	
}
