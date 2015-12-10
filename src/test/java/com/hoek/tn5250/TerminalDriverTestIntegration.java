package com.hoek.tn5250;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tn5250j.TN5250jConstants;

public class TerminalDriverTestIntegration {

	private TerminalDriver driver;
	private String username;
    private String password;

	@Before
	public void setup() {
		driver = new TerminalDriver("SIDEV", "23", "1141");
		username = System.getProperty("username");
        password = System.getProperty("password");
        assertNotNull("-Dusername= not specified", username);
        assertNotNull("-Dpassword= not specified", password);
	}

	@After
	public void teardown() {
		if (driver != null) {
			driver.disconnect();
		}
	}

	@Test
	public void should_connect_with_pub1() throws InterruptedException {
		driver.connect();
		assertTrue(driver.isConnected());
		driver.fillFieldWith("Benutzer", username);
		driver.fillFieldWith("Kennwort", password);
		driver.sendEnter();
		driver.assertScreen("MAIN");
		driver.sendCommand("LKW");
		driver.sendEnter();
		driver.dumpScreen();
		assertTrue(driver.getScreenContent().getLine(2).contains("Einstiegsschirm"));
		char[] color = driver.getScreenContent().getScreen().getData(1, 1, 1, 2, TN5250jConstants.PLANE_COLOR);
		char[] attribute = driver.getScreenContent().getScreen().getData(1, 1, 1, 2, TN5250jConstants.PLANE_ATTR);
		System.out.println(new String(color));
		System.out.println(new String(attribute));
		driver.sendKeys("[pf12]");
		driver.sendCommand("SIGNOFF");
		driver.disconnect();
	}
}
