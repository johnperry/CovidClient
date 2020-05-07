package org.covid;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;
import org.apache.log4j.*;
import org.rsna.ui.ColorPane;
import org.rsna.ui.RowLayout;
import org.rsna.util.FileUtil;
import org.rsna.util.IPUtil;
import org.rsna.util.StringUtil;


public class ExportPanel extends BasePanel implements ActionListener, KeyListener {

	static final Logger logger = Logger.getLogger(ExportPanel.class);

	Configuration config;
	JScrollPane jsp;
	JButton export;
	JButton refresh;
	JButton clear;
	PanelField repositoryIP;
	PanelField repositoryPort;
	JPanel centerPanel;

	Font mono = new java.awt.Font( "Monospaced", java.awt.Font.BOLD, 12 );
	Font titleFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 18 );
	Font columnHeadingFont = new java.awt.Font( "SansSerif", java.awt.Font.BOLD, 14 );

	static ExportPanel exportPanel = null;

	public static synchronized ExportPanel getInstance() {
		if (exportPanel == null) exportPanel = new ExportPanel();
		return exportPanel;
	}

	protected ExportPanel() {
		super();
		config = Configuration.getInstance();
	
		//UI Components
		export = new JButton("Export");
		export.addActionListener(this);
		refresh = new JButton("Refresh");
		refresh.addActionListener(this);
		clear = new JButton("Clear All");
		clear.addActionListener(this);
		
		String ipString = config.getProps().getProperty("repositoryIP","");
		repositoryIP = new PanelField(ipString, 150);
		repositoryIP.addKeyListener(this);
		String portString = config.getProps().getProperty("repositoryPort", "8080");
		repositoryPort = new PanelField(portString);
		repositoryPort.addKeyListener(this);
		
		//Header
		Box header = Box.createHorizontalBox();
		header.setBackground(config.background);
		header.add(new JLabel(" Repository URL:  "));
		header.add(repositoryIP);
		header.add(new JLabel(" : "));
		header.add(repositoryPort);
		header.add(Box.createHorizontalGlue());
		add(header, BorderLayout.NORTH);

		//Main panel
		BasePanel mainPanel = new BasePanel();
		Border inner = BorderFactory.createEmptyBorder(2, 0, 0, 0);
		Border outer = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
		mainPanel.setBorder(BorderFactory.createCompoundBorder(outer, inner));
		//Put in the title
		JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 10));
		titlePanel.setBackground(config.background);
		JLabel panelTitle = new JLabel("Export Metadata and Studies");
		panelTitle.setFont(titleFont);
		panelTitle.setForeground(Color.BLUE);
		titlePanel.add(panelTitle);
		mainPanel.add(titlePanel, BorderLayout.NORTH);
		//Put a scroll pane in the center panel
		JScrollPane jsp = new JScrollPane();
		jsp.setBackground(config.background);
		jsp.getViewport().setBackground(config.background);
		mainPanel.add(jsp, BorderLayout.CENTER);
		//Make a panel to hold the table datasets
		JPanel cp = new JPanel(new FlowLayout(FlowLayout.CENTER));
		cp.setBackground(config.background);
		centerPanel = new BasePanel();
		centerPanel.setLayout(new RowLayout(20, 5));
		cp.add(centerPanel);
		jsp.setViewportView(cp);
		//Now put the main panel in the center of the parent layout
		add(mainPanel, BorderLayout.CENTER);
		
		listCases();
		
		//Footer
		Box footer = Box.createHorizontalBox();
		footer.setBackground(config.background);
		footer.add(clear);
		footer.add(Box.createHorizontalStrut(10));
		footer.add(refresh);
		footer.add(Box.createHorizontalGlue());
		footer.add(export);
		add(footer, BorderLayout.SOUTH);
	}
	
	private void listCases() {
		centerPanel.removeAll();
		//Put in a vertical margin
		centerPanel.add(Box.createVerticalStrut(10));
		centerPanel.add(RowLayout.crlf());
		//Put in the column headings
		centerPanel.add(Box.createHorizontalStrut(5)); //no heading for the checkboxes
		centerPanel.add(new HeadingLabel("PatientID"));
		centerPanel.add(new HeadingLabel("Metadata"));
		centerPanel.add(new HeadingLabel("Export"));
		centerPanel.add(RowLayout.crlf());
		//Put in the cases
		File[] cases = config.getStorageDir().listFiles();
		for (File caseDir : cases) {
			if (caseDir.isDirectory()) {
				String metadataDate = "";
				File metadata = new File(caseDir, "metadata.xml");
				if (metadata.exists()) {
					metadataDate = StringUtil.getDate(metadata.lastModified(), ".");
				}
				String exportDate = "";
				File export = new File(caseDir, "..export");
				if (export.exists()) {
					exportDate = StringUtil.getDate(export.lastModified(), ".");
				}
				centerPanel.add(new CaseCheckBox(caseDir));
				centerPanel.add(new CaseLabel(caseDir.getName()));
				centerPanel.add(new CaseLabel(metadataDate));
				centerPanel.add(new CaseLabel(exportDate));
				centerPanel.add(RowLayout.crlf());
			}
		}		
	}
	
	public void actionPerformed(ActionEvent event) {
		Object source = event.getSource();
		if (source.equals(export)) {
			startExport();
		}
		else if (source.equals(clear)) {
			Component[] comps = centerPanel.getComponents();
			for (Component c : comps) {
				if (c instanceof CaseCheckBox) {
					((CaseCheckBox)c).setSelected(false);
				}
			}
		}
		else if (source.equals(refresh)) {
			listCases();
			revalidate();
		}
	}
	
	public void keyTyped(KeyEvent event) { }
	public void keyPressed(KeyEvent event) { }
	public void keyReleased(KeyEvent event) {
		config.getProps().setProperty("repositoryPort", repositoryPort.getText().trim());
		config.getProps().setProperty("repositoryIP", repositoryIP.getText().trim());
	}
	
	class PanelField extends JTextField {
		public PanelField(String text) {
			this(text, 40);
		}
		public PanelField(String text, int width) {
			super(text);
			setFont(mono);
			Dimension d = getPreferredSize();
			d.width = width;
			setMaximumSize(d);
			setMinimumSize(d);
			setPreferredSize(d);
		}
	}
	
	class HeadingLabel extends JLabel {
		public HeadingLabel(String text) {
			this(text, 0.0f);
		}
		public HeadingLabel(String text, float alignmentX) {
			super(text);
			setFont(columnHeadingFont);
			setForeground(Color.BLUE);
			setAlignmentX(alignmentX);
		}
	}
	
	class CaseCheckBox extends JCheckBox {
		File file;
		public CaseCheckBox(File file) {
			super();
			setBackground(config.background);
			this.file = file;
		}
	}
	
	class CaseLabel extends JLabel {
		public CaseLabel(String text) {
			this(text, 0.0f);
		}
		public CaseLabel(String text, float alignmentX) {
			super(text);
			setFont( mono );
			setAlignmentX(alignmentX);
		}
	}

	private void startExport() {
		try {
		}
		catch (Exception ex) {
			logger.warn("Export failed", ex);
		}
	}
	
	class ExportThread extends Thread {
		public ExportThread() {
			super();
		}
		public void run() {
		}
	}
	
}
