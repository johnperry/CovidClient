package org.covid;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class RegistrationDialog extends DialogPanel {

	String siteID;

	public RegistrationDialog(String siteID, String email, String sitename, String username) {
		super();
		this.siteID = siteID;
		addH("Register Your Site");
		addP("Enter your institution name", "left");
		addParam("sitename", "Institution", sitename, false);
		space(5);
		addP("Enter your name", "left");
		addParam("username", "Name", username, false);
		space(5);
		addP("Enter your email address", "left");
		addParam("email", "Email", email, false);
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
