/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.smoketest;

import static io.restassured.RestAssured.baseURI;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.preemptive;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.base.Predicate;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public class DaemonConfigPageIT extends OpenNMSSeleniumTestCase {
    @Rule
    public Timeout timeout = new Timeout(10, TimeUnit.MINUTES);

    @Before
    public void setUp() {
        RestAssured.baseURI = getBaseUrl();
        RestAssured.port = getServerHttpPort();
        RestAssured.basePath = "/opennms/admin/daemons/index.jsp";
        RestAssured.authentication = preemptive().basic(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        // Reduces the XPath Find Element Waiting time to 5 seconds
        setImplicitWait(5, TimeUnit.SECONDS);
    }

    @After
    public void tearDown() {
        RestAssured.reset();

        // Resets the FindElement Waiting Time
        setImplicitWait();
    }

    private int expectedDaemonCount;

    @Test
    public void verifyDaemonListIsShown() {
        given().get().then()
                .assertThat().statusCode(200)
                .assertThat().contentType(ContentType.HTML);

        expectedDaemonCount = 30;

        DaemonReloadPage page = new DaemonReloadPage().open();
        Assert.assertThat(page.getDaemonRows(), hasSize(30));

        Daemon eventd = page.getDaemon("Eventd");

        Daemon alarmd = page.getDaemon("Alarmd");
        Daemon snmppoller = page.getDaemon("SnmpPoller");
        Daemon ticketer = page.getDaemon("Ticketer");

        Assert.assertThat(eventd.getName(), is("Eventd"));
        Assert.assertThat(eventd.getStatus(), is("running"));
        Assert.assertThat(eventd.isReloadable(), is(true));

        Assert.assertThat(alarmd.getName(), is("Alarmd"));
        Assert.assertThat(alarmd.getStatus(), is("running"));
        Assert.assertThat(alarmd.isReloadable(), is(true));

        Assert.assertThat(snmppoller.getName(), is("SnmpPoller"));
        Assert.assertThat(snmppoller.getStatus(), is("not running"));
        Assert.assertThat(snmppoller.isReloadable(), is(false));

        Assert.assertThat(ticketer.getName(), is("Ticketer"));
        Assert.assertThat(ticketer.getStatus(), is("running"));
        Assert.assertThat(ticketer.isReloadable(), is(false));


        Assert.assertThat(eventd.reload(5), is("Success"));
        Assert.assertThat(eventd.getCurlString(), is(getExceptedCurlStringForDaemonName(eventd.getName())));

        Assert.assertThat(alarmd.reload(5), is("Success"));
        Assert.assertThat(alarmd.getCurlString(), is(getExceptedCurlStringForDaemonName(alarmd.getName())));
    }

    private String getExceptedCurlStringForDaemonName(String name){
        return String.format("curl --request POST -u USERNAME:PASSWORD -url %sopennms/rest/daemons/reload/%s/", getBaseUrl(), name);
    }

    private class Daemon {
        private final String daemonName;

        public Daemon(String daemonName) {
            this.daemonName = daemonName;
        }

        public String getName() {
            // Assert That Right Name is shown
            WebElement nameCell = getDaemonRowElement().findElement(By.xpath("./td[1]"));
            Assert.assertThat(nameCell.isDisplayed(), is(true));
            Assert.assertThat(nameCell.isEnabled(), is(true));
            return nameCell.getText();
        }

        public String getStatus() {
            WebElement label = getDaemonRowElement().findElement(By.xpath("./td[2]/label"));
            Assert.assertThat(label.isDisplayed(), is(true));
            Assert.assertThat(label.isEnabled(), is(true));
            return label.getText();
        }

        public boolean isReloadable() {
            WebElement reloadCell = getDaemonRowElement().findElement(By.xpath("./td[3]"));
            Assert.assertThat(reloadCell.isDisplayed(), is(true));
            Assert.assertThat(reloadCell.isEnabled(), is(true));
            return !reloadCell.getText().equals("");
        }

        public String reload(long maxReloadTimeInSeconds) {
            if (!this.isReloadable()) {
                throw new IllegalStateException("This daemon is not reloadable");
            }
            WebElement reloadButton = getDaemonRowElement().findElement(By.xpath("./td[3]/div/button"));
            Assert.assertThat(reloadButton.isDisplayed(), is(true));
            Assert.assertThat(reloadButton.isEnabled(), is(true));
            reloadButton.click();

            //Check Ui State while reloading is taking place
            WebElement reloadCell = getDaemonRowElement().findElement(By.xpath("./td[3]"));
            reloadButton = reloadCell.findElement(By.xpath("./div/button"));
            Assert.assertThat(reloadButton.isDisplayed(), is(true));
            Assert.assertThat(reloadButton.isEnabled(), is(false));

            WebElement reloadCellStateSpan = reloadCell.findElement(By.xpath("./div/span"));
            Assert.assertThat(reloadCellStateSpan.isDisplayed(), is(true));
            Assert.assertThat(reloadCellStateSpan.getText(), is("Reloading..."));

            //Wait for the Reload to terminate
            new WebDriverWait(m_driver, maxReloadTimeInSeconds).until((Predicate<WebDriver>) (driver) -> {
                final String reloadStateText = getDaemonRowElement().findElement(By.xpath("./td[3]/div/span")).getText();
                return (reloadStateText.equals("Success") || reloadStateText.equals("Failed") || reloadStateText.equals("Unknown"));
            });

            //Check Ui State after the Reload terminated and return the reloadStateText
            reloadCell = getDaemonRowElement().findElement(By.xpath("./td[3]"));
            reloadButton = reloadCell.findElement(By.xpath("./div/button"));
            Assert.assertThat(reloadButton.isDisplayed(), is(true));
            Assert.assertThat(reloadButton.isEnabled(), is(true));

            reloadCellStateSpan = reloadCell.findElement(By.xpath("./div/span"));
            Assert.assertThat(reloadCellStateSpan.isDisplayed(), is(true));

            return reloadCellStateSpan.getText();
        }

        public String getCurlString() {
            if (!this.isReloadable()) {
                throw new IllegalStateException("This daemon is not reloadable");
            }

            WebElement preElement = getDaemonRowElement().findElement(By.xpath("./td[3]//pre"));
            Assert.assertThat(preElement.isDisplayed(), is(false));
            Assert.assertThat(preElement.isEnabled(), is(true));

            WebElement curlButton = getDaemonRowElement().findElement(By.xpath("./td[3]/div/span/button"));
            Assert.assertThat(curlButton.isDisplayed(), is(true));
            Assert.assertThat(curlButton.isEnabled(), is(true));
            curlButton.click();

            preElement = getDaemonRowElement().findElement(By.xpath("./td[3]//pre"));
            Assert.assertThat(preElement.isDisplayed(), is(true));
            Assert.assertThat(preElement.isEnabled(), is(true));

            final String curlString = preElement.getText();

            curlButton = getDaemonRowElement().findElement(By.xpath(".//div[contains(@class, 'modal-footer')]/button"));
            Assert.assertThat(curlButton.isDisplayed(), is(true));
            Assert.assertThat(curlButton.isEnabled(), is(true));
            curlButton.click();

            preElement = getDaemonRowElement().findElement(By.xpath("./td[3]//pre"));
            Assert.assertThat(preElement.isDisplayed(), is(false));
            Assert.assertThat(preElement.isEnabled(), is(true));

            return curlString;
        }

        private WebElement getDaemonRowElement() {
            final String curlXpathExpression = String.format("//table/tbody/tr/td[contains(text(), '%s')]/..", daemonName);
            return m_driver.findElement(By.xpath(curlXpathExpression));
        }
    }

    private class DaemonReloadPage {

        public DaemonReloadPage open() {
            m_driver.get(baseURI + "opennms/admin/daemons/index.jsp");
            new WebDriverWait(m_driver, 5).until((Predicate<WebDriver>) (driver) -> getDaemonRows().size() == expectedDaemonCount);
            return this;
        }

        public List<WebElement> getDaemonRows() {
            return m_driver.findElements(By.xpath("//table/tbody/tr"));
        }

        public Daemon getDaemon(String daemonName) {
            return new Daemon(daemonName);
        }
    }
}
