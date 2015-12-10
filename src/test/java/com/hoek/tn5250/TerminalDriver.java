package com.hoek.tn5250;

import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tn5250j.Session5250;
import org.tn5250j.TN5250jConstants;
import org.tn5250j.event.ScreenOIAListener;
import org.tn5250j.event.SessionChangeEvent;
import org.tn5250j.event.SessionListener;
import org.tn5250j.framework.common.SessionManager;
import org.tn5250j.framework.tn5250.Screen5250;
import org.tn5250j.framework.tn5250.ScreenField;
import org.tn5250j.framework.tn5250.ScreenOIA;

public class TerminalDriver implements SessionListener {

	private static final int _100 = 100;

	Logger LOG = Logger.getLogger(TerminalDriver.class);

	private final Properties properties;
	private Session5250 session;
	private boolean connected;
	private boolean informed;
	private int informChange;
	private Screen5250 screen;
	private int timeout = 30000;

	/**
	 * @param host
	 * @param port
	 */
	public TerminalDriver(String host, String port, String codepage) {
		properties = new Properties();
		properties.put(TN5250jConstants.SESSION_HOST, host);
		properties.put(TN5250jConstants.SESSION_HOST_PORT, port);
		properties.put(TN5250jConstants.SESSION_CODE_PAGE, codepage);
		properties.put(TN5250jConstants.SESSION_TN_ENHANCED, "1");
		properties.put(TN5250jConstants.SESSION_TERM_NAME_SYSTEM, "1");
		properties.put(TN5250jConstants.SESSION_SCREEN_SIZE, TN5250jConstants.SCREEN_SIZE_27X132_STR);
		properties.put(TN5250jConstants.SESSION_PROXY_PORT, "1080");
		Logger.getRootLogger().setLevel(Level.DEBUG);
	}

	/**
	 * @return the timeout
	 */
	public int getTimeout() {
		return timeout;
	}

	/**
	 * @param timeout
	 *            the timeout to set
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	/**
	 * @return
	 * @throws InterruptedException
	 */
	public boolean connect() throws InterruptedException {
		if (SessionManager.instance().getSessions().getCount() == 0) {
			createSession();
			session.connect();
		} else {
			session = SessionManager.instance().getSessions().item(0);
		}
		int count = 0;
		int max = getTimeout() / _100;
		while (!connected && count <= max) {
			Thread.sleep(_100);
			count++;
		}
		if (count > max && !connected) {
			return false;
		}
		screen = session.getScreen();
		addOperatorInformationAreaListener();
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		if (session != null)
			disconnect();
		super.finalize();
	}

	public void disconnect() {
		session.disconnect();
	}

	private void createSession() {
		session = SessionManager.instance().openSession(properties, null, "SIDEV");
		session.addSessionListener(this);
	}

	private void addOperatorInformationAreaListener() {
		session.getScreen().getOIA().addOIAListener(new ScreenOIAListener() {
			@Override
			public void onOIAChanged(ScreenOIA oia, int change) {
				LOG.debug(String.format("OIA %d", change));
				informed = informed | informChange == change;
			}
		});
	}

	public void waitForUnlock() {
		informed = false;
		informChange = ScreenOIAListener.OIA_CHANGED_KEYBOARD_LOCKED;
		while (!informed) {
			Thread.yield();
		}
		dumpScreen();

		if (getScreenContent().getLine(0).contains("Display Program Messages")) {
			sendKeys("[enter]").waitForUnlock();
		}
	}

	public boolean isConnected() {
		return connected;
	}

	public TerminalDriver sendKeys(String keys) {
		screen.sendKeys(keys);
		return this;
	}

	public TerminalDriver fillCurrentField(String text) {
		ScreenField currentField = screen.getScreenFields().getCurrentField();
		LOG.info(String.format("cursor: %d %d", currentField.startRow(), currentField.startCol()));
		currentField.setString(text);
		return this;
	}

	public TerminalDriver select(String label, String text) {
		ScreenContent content = getScreenContent();
		int index = content.indexOf(label);
		ScreenField field = null;
		while (field == null && index > 0) {
			field = screen.getScreenFields().findByPosition(index--);
		}
		if (field == null) {
			throw new IllegalStateException(String.format("Could not find field *before* label %s"));
		}
		field.setString(text);
		return this;
	}

	public TerminalDriver fillFieldWith(String label, String text) {
		ScreenContent content = getScreenContent();
		int index = content.indexOf(label);
		ScreenField field = null;
		while (field == null && index < content.length()) {
			field = screen.getScreenFields().findByPosition(index++);
		}
		if (field == null) {
			throw new IllegalStateException(String.format("Could not find field *after* label %s"));
		}
		field.setString(text);
		return this;
	}

	public void dumpScreen() {
		getScreenContent().dumpScreen();
	}

	public void assertScreen(String name) {
		assert getScreenContent().getLine(0).contains(name) : String.format("Screen is not '%s'", name);
	}

	public ScreenContent getScreenContent() {
		return new ScreenContent(session);
	}

	public void sendCommand(String command) {
		fillFieldWith("===>", command).sendEnter();
	}

	public TerminalDriver sendEnter() {
		sendKeys("[enter]").waitForUnlock();
		return this;
	}

	public String lastReportLine() {
		ScreenContent content = getScreenContent();
		fillFieldWith("Position to line", "B").sendEnter();
		return content.getLine(content.lineOf("********  End of report  ********") - 1);
	}

	@Override
	public void onSessionChanged(SessionChangeEvent changeEvent) {
		connected = changeEvent.getState() == TN5250jConstants.STATE_CONNECTED;
	}
}
