package org.covid;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class RegistrationDialog extends DialogPanel {

	String siteID;

	public RegistrationDialog(
				String siteID, 
				String username, 
				String email, 
				String phone, 
				String sitename, 
				String adrs1, 
				String adrs2, 
				String adrs3) {
		super();
		this.siteID = siteID;
		addH("Register Your Site");
		//addP("Enter your name", "left");
		addParam("username", "Name", username, false);
		space(5);
		//addP("Enter your email address", "left");
		addParam("email", "Email", email, false);
		space(5);
		//addP("Enter your telephone number", "left");
		addParam("phone", "Telephone", phone, false);
		space(5);
		//addP("Enter your institution name", "left");
		addParam("sitename", "Institution Name", sitename, false);
		addParam("adrs1", "Address", adrs1, false);
		addParam("adrs2", "", adrs2, false);
		addParam("adrs3", "", adrs3, false);
		addCheckBox("accept","I agree to the Creative Commons attribution license.");
		space(5);
	}
	
	public String getEmail() {
		return getParam("email");
	}
	
	public String getSitename() {
		return getParam("sitename");
	}
	
	public String getUsername() {
		return getParam("username");
	}
	
	public boolean show(Component parent) {
		int result = JOptionPane.showOptionDialog(
				parent,
				this,
				"Register",
				JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null, //icon
				null, //options
				null); //initialValue
		return (result == JOptionPane.OK_OPTION);
	}		
}
