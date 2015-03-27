package org.IntelligentSigma.selenium;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A base class for a Selenium/WebDriver UI Test project that contains helpful methods that are much more robust than
 * many of the simple selenium methods.
 * It should typically NOT be used in test classes, only in page object classes.
 *
 * @author David Mamanakis
 * @author Eric Pabst
 * @author Tyler Peterson
 * @author Jared Green
 * @author Kent Olsen
 */
public class BaseSeleniumPage {
  protected static final Logger LOG = Logger.getLogger(BaseSeleniumPage.class);
  private final int WAIT_TIME = 30; //default wait timer for the explicit waits, adjustable
  private final ArrayList<String> errorList = new ArrayList<String>();
  protected WebDriver driver;

  /**
   * Initialization and passage of the Web Driver.
   *
   * @param webDriver remote or local webDriver
   */
  public BaseSeleniumPage(WebDriver webDriver) {
    super();
    this.driver = webDriver;
  }

  /**
   * getMethodName: returns the name of the currently running java method
   *
   * @return Name of Currently Executing Method
   */
  public static String getMethodName() {
    final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
    return ste[3].getMethodName();
  }

  /**
   * getClassName: returns the name of the currently running java class
   *
   * @return Name of Currently Executing Class
   */
  public static String getClassName() {
    final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
    return ste[3].getClassName();
  }

  /**
   * executeJavascript: executes a string of JavaScript
   *
   * @param script - string of the javascript to run
   * @param driver - the current session driver
   * @return Reply Object from the JS Executor
   */
  public static Object executeJavascript(String script, WebDriver driver) {
    LOG.info("Executing javascript: " + script);
    JavascriptExecutor js = (JavascriptExecutor) driver;
    return js.executeScript(script);
  }

  /**
   * getDriver: returns the current driver
   *
   * @return Web Driver
   */
  protected WebDriver getDriver() {
    return driver;
  }

  /**
   * waitForElementsPresent: waits for at least one match, then returns all matches that are present.
   *
   * @param selector - a string representing the CSS or HTML selector
   * @return The LIST of Elements matching this CSS Selector
   */
  protected List<WebElement> waitForElementsPresent(String selector) {
    waitForElementPresent(selector);
    return getDriver().findElements(By.cssSelector(selector));
  }

  /**
   * waitForElementPresent: waits for one specific element on the page to be present, has an override to allow a variable
   * timeout for the selector.
   *
   * @param selector the CSS selector
   * @return the Web Element matching the CSS Selector
   */
  protected WebElement waitForElementPresent(String selector) {
    return waitForElementPresent(selector, WAIT_TIME);
  }

  /**
   * waitForElementPresent: waits for one specific element on the page to be present, is an override to allow a variable
   * timeout for the selector.
   *
   * @param selector the CSS selector
   * @param timeOutInSeconds the requested timeout
   * @return the Web Element matching the CSS Selector in X seconds
   */
  protected WebElement waitForElementPresent(String selector, int timeOutInSeconds) {
    WebDriverWait wait = new WebDriverWait(getDriver(), timeOutInSeconds);
    try {
      return wait.until(presenceOfElementLocated(selector));
    } catch (TimeoutException e) {
      throw new TimeoutException("waitForElementPresent(\"" + selector + "\")", e);
    }
  }

  /**
   * logDom: logs the DOM so that the Engineer may go see what the page looked like at the time of failure
   *
   * @param testName the name of the test
   * @param contextDescription the Context
   * @param throwable error to be thrown
   */
  protected void logDom(String testName, String contextDescription, Throwable throwable) {
    if (throwable == null) {
      throw new IllegalArgumentException("throwable must not be null");
    }
    if (getDriver() == null) {
      LOG.fatal("logDom aborted because driver=null.");
      return;
    }

    // Other possible improvements:
    //    - capture the stack trace in the html.
    //    - make it look still more like the original web page, somehow.

    // Not using "driver.getPageSource()" alone, because the documentation gives no confidence it will include the latest
    // changes, as opposed to what was loaded from the server.  Also, the best javascript syntax is uncertain: a very quick
    // googling yields question on whether it's cross-browser or cross-OS. Like
    //   http://stackoverflow.com/questions/817218/get-entire-document-html-as-string
    // So this much returns just the <body>...</body> section of the page:

//    try {
//      //Add console log msgs and cookies to the dom
//      executeJavascript("if (typeof() !== \"undefined\") { addLogsToDom(); addCookiesToDom(); }");
//    } catch (WebDriverException e) {
//      LOG.warn("Problem running addLogsToDom().", e);
//    }

    String innerHtml = (String) executeJavascript("return document.documentElement.innerHTML;");
    if (innerHtml == null) {
      LOG.fatal("Possibly encountered a TimeoutException, but attempt to capture innerHtml returned null.");
    } else {
      String html = "<html>" + innerHtml + "</html>";
      // Disable scripting so that the page integrity is preserved.
      // Comment out tags and leave in page to know what webclient originally served.
      String htmlWithFixes = html.replaceAll("((?s)<script.*?<\\/script>)", "\n<!-- Disabled to preserve page integrity $1 -->").
          // Add jquery for convenience with debugging such as running $(".myElement .mySubElement"); in the Javascript console.
              // Add base href to make images and css resolve properly.
              replace("<head>", "<head><script type=\"text/javascript\" " +
              "src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js\"></script><base href=\"" +
              getDriver().getCurrentUrl() + "\"/>");
      File dir = new File("target/screenshots");
      if ((!dir.mkdirs()) && (!dir.exists())) {
        throw new RuntimeException("Unable to create directory for " + dir.getAbsolutePath());
      }
      // This filename is chosen (with "failure-report-*") to cause EC to create a link to it.
      // It might be even more convenient to create an html link in the LOG output below, and/or link this file
      // into the test result shown inline in the step result by the EC postp work.
      String filename = testName + "-" + System.currentTimeMillis() + "-failure-report-snapshot.html";
      File file = new File(dir, filename);
      try {
        FileUtils.writeStringToFile(file, htmlWithFixes);
        String message = "Encountered a problem.  " + (contextDescription != null ? "Context: " + contextDescription + ".  " : "");
        LOG.fatal(message + "Saving html to file that should become linked to the EC job: " + file.getAbsolutePath(), throwable);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * presenceOfElementLocated: is a method to detect the presence of an element, seldom used
   *
   * @param locator the By locator
   * @return the WebElement matching the BY Locator
   */
  protected Function<WebDriver, WebElement> presenceOfElementLocated(final By locator) {
    return new Function<WebDriver, WebElement>() {

      public WebElement apply(WebDriver driver) {
        return driver.findElement(locator);
      }
    };
  }

  /**
   * presenceOfElementLocated: is a method to detect the presence of an element, preferred over previous
   *
   * @param selector the CSS selector
   * @return the WebElement matching the String Selector
   */
  protected Function<WebDriver, WebElement> presenceOfElementLocated(final String selector) {
    return new Function<WebDriver, WebElement>() {

      public WebElement apply(WebDriver driver) {
        return findElement(selector);
      }
    };
  }

  /**
   * elementCountEquals: is a method to detect the presence of a number of Elements
   *
   * @param selector the CSS selector
   * @return the list of Elements found by the selector
   */
  protected Function<WebDriver, List<WebElement>> elementCountEquals(final String selector, final int expectedCount) {
    return new Function<WebDriver, List<WebElement>>() {

      public List<WebElement> apply(WebDriver driver) {
        List<WebElement> elements = findElements(selector);
        if (elements.size() != expectedCount) {
          // Fail the condition check since the expected count has not been met.
          elements = null;
        }
        return elements;
      }
    };
  }

  /**
   * waitForElementNotPresent: is a method to wait for a specific element on the page to be gone
   *
   * @param selector the CSS selector
   */
  public void waitForElementNotPresent(final String selector) {
    WebDriverWait wait = new WebDriverWait(getDriver(), WAIT_TIME);
    try {
      wait.until(not(new ExpectedCondition<WebElement>() {

        public WebElement apply(WebDriver driver) {
          return findElement(selector);
        }
      }));
    } catch (TimeoutException e) {
      throw new TimeoutException("waitForElementNotPresent(\"" + selector + "\")", e);
    }
  }

  /**
   * assertElementNotShown: verifies the selector is no longer present on the page
   *
   * @param selector the CSS selector
   * @param failMessage the message to display
   */
  public void assertElementNotShown(String selector, String failMessage) {
    try {
      waitForWebElementToBeDisplayed(selector);
      Assert.fail(failMessage);
    } catch (Exception e) {
      // expected exception
      LOG.info(selector + " was not found.");
    }
  }

  /**
   * assertElementNotPresent: verifies the selector is no longer present on the page
   *
   * @param selector the CSS selector
   */
  protected void assertElementNotPresent(String selector) {
    if (findElement(selector) != null) {
      Assert.fail("Element should not be present: " + selector);
    }
  }

  /**
   * assertElementPresent: verifies an element matching the selector is found
   *
   * @param selector the CSS selector
   * @return WebElement of Selector
   */
  protected WebElement assertElementPresent(String selector) {
    WebElement element = findElement(selector);
    if (element == null) {
      Assert.fail("Element should be present: " + selector);
    }
    return element;
  }

  /**
   * assertElementPresentWithTextSubstring: verifies a selector with the Specified Text Element exists on the page.
   *
   * @param selector the CSS selector
   * @param substring the substring to find
   * @return the webElement containing the substring
   */
  protected WebElement assertElementPresentWithTextSubstring(String selector, String substring) {
    for (WebElement element : findElements(selector)) {
      if (element.getText().contains(substring)) {
        return element;
      }
    }
    assertSubstring(substring, assertElementPresent(selector).getText());
    throw new RuntimeException("an earlier check should have thrown an exception");
  }

  /**
   * assertSubstring: verifies the substring exists
   *
   * @param substring the substring to find
   * @param string the String to find substring
   */
  protected void assertSubstring(String substring, String string) {
    boolean success = string.contains(substring);
    if (!success) {
      Assert.fail("Expected '" + substring + "' in '" + string + "'");
    }
  }

  /**
   * ExpectedCondition: verifies if something is true or false (exists or not)
   *
   * @param toInvert expectedCondition
   * @return Boolean -- True, False, Null
   */
  protected ExpectedCondition<Boolean> not(final ExpectedCondition<?> toInvert) {
    return new ExpectedCondition<Boolean>() {

      public Boolean apply(WebDriver driver) {
        Object result = toInvert.apply(driver);
        return (result == null || result == Boolean.FALSE);
      }
    };
  }

  /**
   * convertToBy: is a method that follows the general guidlines of Selenium Element Locators as defined at:
   * http://release.seleniumhq.org/selenium-remote-control/0.9.2/doc/dotnet/Selenium.ISelenium.html
   * We use this method to simplify the "find element by" commands:
   * waitForElementPresent("#ID .CLASS div a[ATTRIBUTE=\"something\"])
   * or
   * waitForElementPresent("//div/")
   * or
   * waitForElementPresent("id=happy")
   * or just about any other combination... see comments in-line.
   *
   * @param selector the CSS selector
   * @return the By
   */
  private By convertToBy(String selector) {
    if (selector == null || selector.isEmpty()) {
      throw new InvalidSelectorException("Invalid parameter: " + selector);
    }

    String[] parts = selector.split("=");
    By findBy;

    if (parts.length > 1) {
      String getType = parts[0];
      String key = selector.substring(getType.length() + 1); //Separate the type and the key on the '=' character

      if (getType.equalsIgnoreCase("css")) { //Check to see if it is CSS
        findBy = By.cssSelector(key);  //"css=#something.somethingElse" should findBy By.cssSelector <id="something"
                                       // class="somethingElse">
      }
      else if (getType.equalsIgnoreCase("identifier") || getType.equalsIgnoreCase("id")) { //Check to see if it is an ID
        findBy = By.id(key);  //"identifier=something" should findBy By.id <id="something">
      }
      else if (getType.equalsIgnoreCase("link")) { //Check to see if it is a LINK
        findBy = By.linkText(key);  //"link=something" should findBy By.linkText <a href="http://www.google.com/search?q=something">something</a>
      }
      else if (getType.equalsIgnoreCase("name")) { //Check to see if it is a NAME
        findBy = By.name(key);  //"name=something" should findBy By.name <id="dude" name="something">
      }
      else if (getType.equalsIgnoreCase("tag")) { //Check to see if it is a TAG
        findBy = By.tagName(key);  //"tag=a" should findBy By.tagName <a href="http://www.google.com/search?q=something">something</a>
      }
      else if (getType.equalsIgnoreCase("xpath")) { //Check to see if it is an XPATH
        findBy = By.xpath(key);  //"xpath=//div/article/div[4]/div[3]" should findBy By.xpath first div, first article in first div,
                                 // 4 divs down inside article, 3 divs down inside the 4th div down
      }
      else {
        findBy = null; //If it is neither identified as CSS, an ID, a LINK, a NAME, a TAG, or an XPATH, then make it NULL
      }
    }
    else {
      findBy = null; //If it is empty, make it NULL
    }

    //if findBy is NULL, check to see if it is XPATH with a single "/" (more generic search), or check if it is an ID,
    // Class, Attribute, Tag, etc... and if not, assume it is CSS, try anyway... or run it as ID and fail.
    if (findBy == null) { //CHeck to see if findBy is a NULL, which means the string isn't identified, or it was empty.
      if (selector.startsWith("/")) { //Check to see if it is a default XPATH
        findBy = By.xpath(selector);  //If it starts with "//" or "/" it is an XPATH    (// = more specific, / = less specific)
      }
      else if (selector.startsWith("#") || selector.startsWith(".") || selector.startsWith("[")) { //Check to see if it is an
                                                                                                                     // ID (#), a CLASS (.) or an ATTRIBUTE ([)
        findBy = By.cssSelector(selector); //If any of these (CLASS, ID, ATTRIBUTE) then treat it like CSS
      }
      else if (!selector.isEmpty()) { //If it is NOT XPATH and NOT an ID, CLASS or ATTRIBUTE, then treat it like CSS, it may be a tag or other element
        findBy = By.cssSelector(selector);
      }
      else {  //FAILSAFE: if it is not any of the above, use this to fail the selection.
        findBy = By.id(selector);
      }
    }
    return findBy;
  }

  /**
   * findElement: is a method that finds an element on a page by a String Locator
   *
   * @param selector the CSS selector
   * @return the WebElement or null if not found
   */
  protected WebElement findElement(String selector) {
    By findBy = convertToBy(selector);
    try {
      return getDriver().findElement(findBy);
    } catch (NoSuchElementException e) {
      return null;
    }
  }

  /**
   * findElements: is a method that finds a list of elements on a page by a String Locator
   *
   * @param selector the CSS selector
   * @return a lits of webElement that contain the className
   */
  protected List<WebElement> findElements(String selector) {
    By findBy = convertToBy(selector);
    try {
      return getDriver().findElements(findBy);
    } catch (NoSuchElementException e) {
      return Collections.emptyList();
    }
  }

  /**
   * hasClass: is a method to see if an element has a specific CLASS using a String Selector
   *
   * @param selector the CSS selector
   * @param className the className looking for
   * @return boolean -- TRUE or FALSE
   */
  protected boolean hasClass(String selector, String className  ) {
    return hasClass(waitForElementPresent(selector), className);
  }

  /**
   * hasClass: is a method to see if an element has a specific CLASS using a WebElement
   *
   * @param webElement the webElement to find the className
   * @param className the className looking for
   * @return boolean -- TRUE or FALSE
   */
  protected boolean hasClass(WebElement webElement, String className) {
    boolean hasClass = false;

    if (webElement != null) {
      String classNames = webElement.getAttribute("class");

      if (!classNames.isEmpty()) {
        String[] toks = classNames.split("\\s+");

        for (String tok : toks) {
          if (className.equals(tok)) {
            hasClass = true;
            break;
          }
        }
      }
    }
    return hasClass;
  }

  /**
   * elementExists: is a method to check to see if an ELEMENT exists
   *
   * @param selector the CSS selector
   * @return boolean -- TRUE or FALSE
   */
  protected boolean elementExists(String selector) {
    return (findElement(selector) != null);
  }

  /**
   * scrollTo: will move the focus of the screen to the specified coordinates
   *
   * @param xpos the X locator
   * @param ypos the Y locator
   */
  protected void scrollTo(int xpos, int ypos) {
    executeJavascript("window.scrollTo(" + xpos + "," + ypos + ")");
  }

  protected void scrollIntoView(String idSelector) {
    executeJavascript("document.getElementById('" + idSelector + "').scrollIntoView();");
  }

  /**
   * getTextForElements: will return the text for the elements in a list
   *
   * @param selector the CSS selector
   * @return list of strings taken from the list of elements
   */
  protected List<String> getTextForElements(String selector) {
    List<WebElement> elements = findElements(selector);
    List<String> textList = new ArrayList<String>(elements.size());
    for (WebElement element : elements) {
      textList.add(element.getText());
    }
    return textList;
  }

  /**
   * getText: is a method to get the string from an HTML element
   *
   * @param selector the CSS selector
   * @return String of Text
   */
  protected String getText(String selector) {
    WebElement element = waitForElementPresent(selector);
    try {
      return element.getText();
    } catch (StaleElementReferenceException e) {
      LOG.warn("retrying stale element: " + selector, e);
      return waitForElementPresent(selector).getText();
    }
  }

  /**
   * setInputValue: will set a value passed in as a string into an element that is a type of TEXT input, uses a String Selector
   *
   * @param selector the CSS selector
   * @param string the input value
   */
  protected void setInputValue(String selector, String string) {
    setInputValue(waitForElementPresent(selector), string);
  }

  /**
   * setInputValue: will set a value passed in as a string into an element that is a type of TEXT input, uses a WebElement
   *
   * @param webElement the web element of the Input
   * @param string the input value
   */
  protected void setInputValue(WebElement webElement, String string) {
    webElement.clear();
    if (string != null && string.length() > 0) {
      webElement.sendKeys(string);
    }
  }

  /**
   * assertElementHasNoText: will verify that a specified element has no text associated
   *
   * @param selector the CSS selector
   */
  protected void assertElementHasNoText(String selector) {
    WebElement element = findElement(selector);
    String text = element != null ? element.getText() : "";
    if (!text.isEmpty()) {
      Assert.fail(text + "  Selector=\"" + selector + "\"");
    }
  }

  /**
   * getSelect: will get a "select" item from a String Selector
   *
   * @param selector the CSS selector
   * @return the Select element
   */
  protected Select getSelect(String selector) {
    try {
      return new Select(waitForElementPresent(selector));
    } catch (StaleElementReferenceException e) {
      LOG.warn("retrying stale element: " + selector, e);
      return new Select(waitForElementPresent(selector));
    }
  }

  /**
   * click: is a method to click on an element. It will try the Selenium Click, then fail to the JavaScript Click, then to the Send Keys.
   *
   * @param selector the CSS selector
   * @return the WebElement clicked
   */
  protected WebElement click(String selector) {
    boolean exceptionThrown = true;

    WebElement somethingToClick = waitForElementPresent(selector);
    // Wait for a very small amount of time since we're simulating a human and humans have some natural delay.
    // This gives a tiny amount of time for page setup after an element is present.
    // The intent is to have more consistent test results.
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      // ignore
    }
    int matches = findElements(selector).size();
    if (matches > 1) {
      Assert.fail("The following click selector found count=" + matches + " elements matching selector=\"" + selector + "\"");
    }

    //waitForWebElementToBeDisplayed(somethingToClick);
    LOG.info("Clicking on " + selector + ".");

    try {
      somethingToClick.click();
      exceptionThrown = false;
    } catch (Throwable t) {
      LOG.info("Selenium Click Failed for \"" + selector + "\": Using JavaScript Click.");
    }

    if (exceptionThrown) {
      try {
        executeJavascript("$j('" + selector + "').eq(0).mousedown().mouseup().click();");
        exceptionThrown = false;
      } catch (Throwable t) {
        LOG.info("jQuery Click Failed for " + selector + ": Using the Send Keys \"click\".");
      }
    }

    if (exceptionThrown) {
      try {
        somethingToClick.sendKeys(Keys.ENTER);
      } catch (Throwable t) {
        LOG.info("SendKeys Click Failed for " + selector + ".");
      }
    }
    // Wait for a very small amount of time since we're simulating a human and humans have some natural delay.
    // This gives a tiny amount of time for initial processing.  The intent is to have more consistent test results.
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      // ignore
    }
    waitForJQueryToBeInactive(getDriver());
    return somethingToClick;
  }

  /**
   * clickItemInElementList will take a list of webElements (generated by the "findElements" method) and click on one of those items, as
   * specified by the int.
   *
   * @param webElementList is the list of Web Elements found using "findElements" method
   * @param elementInList is the location of the number in the list you want to click
   */
  protected void clickItemInElementList(List<WebElement> webElementList, int elementInList) {
    webElementList.get(elementInList).click();
  }

  /**
   * clickSvg performs the same basic functions as "click" but it does it based on SVG, suing the JavaScriptExecutor
   *
   * @param clickSelector is the selector for the ClickSVG method to click
   * @param presentSelector is the selector to wait for
   */
  protected void clickSvgAndWaitForElementToBeDisplayed(String clickSelector, String presentSelector) {
    try {
      clickSvgAndWaitForElementPresent(clickSelector, presentSelector);
      waitForWebElementToBeDisplayed(presentSelector);
    } catch (TimeoutException e) {
      LOG.info("Retrying click on selector=\"" + clickSelector + "\" because of exception=\"" + e + "\"");
      clickSvgAndWaitForElementPresent(clickSelector, presentSelector);
      waitForWebElementToBeDisplayed(presentSelector);
    }
  }

  /**
   * clickSvgAndWaitForElementPresent: can be used with SVG on web pages, has a retry.
   *
   * @param clickSelector  the CSS selector to click
   * @param presentSelector  the CSS selector to find
   * @return the WebElement of the requested selector
   */
  protected WebElement clickSvgAndWaitForElementPresent(String clickSelector, String presentSelector) {
    try {
      clickSvg(clickSelector);
    } catch (TimeoutException e) {
      LOG.info("Retrying click on selector=\"" + clickSelector + "\" because of exception=\"" + e + "\"");
      clickSvg(clickSelector);
    }
    waitForJQueryToBeInactive(getDriver());
    return waitForElementPresent(presentSelector);
  }

  /**
   * clickSvg: can be used like "click" with SVG elements on a page
   *
   * @param selector the CSS selector
   */
  protected void clickSvg(String selector) {
    executeJavascript("var e = document.createEvent('SVGEvents');e.initEvent('click',true,true);$('" + selector + "')[0].dispatchEvent(e);");
    waitForJQueryToBeInactive(getDriver());
  }

  /**
   * clickSvgToDismiss: can be used like "clickToDismiss" except with SVG elements.
   *
   * @param selector the CSS selector
   */
  protected void clickSvgToDismiss(String selector) {
    try {
      clickSvg(selector);
    } catch (TimeoutException e) {
      LOG.info("Retrying click on selector=\"" + selector + "\" because of exception=\"" + e + "\"");
      clickSvg(selector);
    }
    waitForJQueryToBeInactive(getDriver());
    waitForElementNotPresent(selector);
  }

  /**
   * clickCheckBox: will click a check box via a String Selector
   *
   * @param selector the CSS selector
   */
  public void clickCheckBox(String selector) {
    WebElement checkBox = click(selector);
    String checked = checkBox.getAttribute("checked");
    if (!"true".equals(checked)) {
      LOG.info("Retrying click on checkbox=\"" + selector + "\" because it says it is still not checked.");
      checkBox = click(selector);
      checked = checkBox.getAttribute("checked");
    }
    Assert.assertEquals(checked, "true", "should be checked: " + selector);
  }

  /**
   * clickToDismiss: can only be used if the button or link is actually removed from the DOM.  If it is simply hidden,
   * don't use this method.
   *
   * @param selector the CSS selector
   */
  protected void clickToDismiss(String selector) {
    try {
      click(selector);
    } catch (TimeoutException e) {
      LOG.info("Retrying click on selector=\"" + selector + "\" because of exception=\"" + e + "\"");
      click(selector);
    }
    waitForJQueryToBeInactive(getDriver());
    waitForElementNotPresent(selector);
  }

  /**
   * clickAndWaitForElementPresent: combines the Click and WaitForElementPresent methods
   *
   * @param clickSelector the CSS selector to click
   * @param presentSelector the CSS selector to find
   * @return the WebElement of the WaitedFor Selector
   */
  protected WebElement clickAndWaitForElementPresent(String clickSelector, String presentSelector) {
    try {
      click(clickSelector);
    } catch (TimeoutException e) {
      LOG.info("Retrying click on selector=\"" + clickSelector + "\" because of exception=\"" + e + "\"");
      click(clickSelector);
    }
    waitForJQueryToBeInactive(getDriver());
    return waitForElementPresent(presentSelector);
  }

  /**
   * clickAndWaitForElementToBeDisplayed: combines the Click, WaitForElementPresent and WaitForElementDisplayed methods
   * The purpose is to wait until the display of a DOM element, not just that it exists. Has a retry.
   *
   * @param clickSelector the CSS selector to click
   * @param presentSelector the CSS selector to find
   */
  protected void clickAndWaitForElementToBeDisplayed(String clickSelector, String presentSelector) {
    try {
      clickAndWaitForElementPresent(clickSelector, presentSelector);
    } catch (TimeoutException e) {
      LOG.info("Retrying click on selector=\"" + clickSelector + "\" because of exception=\"" + e + "\"");
      clickAndWaitForElementPresent(clickSelector, presentSelector);
    }
    waitForWebElementToBeDisplayed(presentSelector);
  }

  /**
   * focusOnClosestLI: will set the focus on a particular item CLOSEST to the desired selector in a list. Can be modified to set focus anywhere
   *
   * @param selector the CSS selector
   */
  protected void focusOnClosestLI(String selector) {
    waitForElementPresent(selector);
    executeJavascript("$j('" + selector + "').closest('li').addClass('focus');");
    // Temporarily adding a sleep because the tombstoneParentChildRelationship test is acting as if the click isn't happening.
    // The idea is that the focusOnClosestLI needs time to take effect.
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) { /* ignore */ }
  }

  /**
   * focusOnClosestElement: will set the focus on a particular item CLOSEST to the desired selector in a list.
   *
   * @param selector the CSS selector
   */
  protected void focusOnClosestElement(String selector, String element) {
    waitForElementPresent(selector);
    executeJavascript("$j('" + selector + "').closest('" + element + "').addClass('focus');");
    // Temporarily adding a sleep because the tombstoneParentChildRelationship test is acting as if the click isn't happening.
    // The idea is that the focusOnClosestLI needs time to take effect.
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) { /* ignore */ }
  }

  /**
   * isEnabled: is a method to see if an element is ENABLED
   *
   * @param selector the CSS selector
   * @return boolean -- TRUE or FALSE
   */
  protected boolean isEnabled(String selector) {
    WebElement element = findElement(selector);
    return isEnabled(element);
  }

  /**
   * isEnabled: is a method to see if an element is ENABLED
   *
   * @param webElement to look for if is enabled
   * @return boolean -- TRUE or FALSE
   */
  protected boolean isEnabled(WebElement webElement) {
    if (webElement == null) {
      throw new RuntimeException("The WebElement is NULL.");
    }
    return webElement.isEnabled();
  }

  /**
   * waitForElementPresentCount: will wait for a specified number of elements are present on the page.
   *
   * @param selector the CSS selector
   * @param expectedCount number of webElements to wait for
   * @return the list of expected web elements
   */
  protected List<WebElement> waitForElementPresentCount(String selector, int expectedCount) {
    WebDriverWait wait = new WebDriverWait(getDriver(), WAIT_TIME);
    try {
      return wait.until(elementCountEquals(selector, expectedCount));
    } catch (TimeoutException e) {
      throw new TimeoutException("waitForElementPresentCount(\"" + selector + "\", " + expectedCount + ")", e);
    }
  }

  /**
   * waitForWebElementToBeDisplayed: will wait for an element to be dieplayed, not just present, uses a string locator
   *
   * @param selector the CSS selector
   * @return the Web Element waited for
   */
  public WebElement waitForWebElementToBeDisplayed(String selector) {
    WebElement webElement = waitForElementPresent(selector);
    try {
      return waitForWebElementToBeDisplayed(webElement);
    } catch (StaleElementReferenceException e) {
      LOG.warn("retrying stale element: " + selector, e);
      return waitForWebElementToBeDisplayed(webElement);
    }
  }

  /**
   * waitForWebElementToBeDisplayed: will wait for an element to be dieplayed, not just present, uses a web element
   *
   * @param webElement the web element waiting for
   * @return the web element that is displayed.
   */
  public WebElement waitForWebElementToBeDisplayed(final WebElement webElement) {
    WebDriverWait waitForWebElement = new WebDriverWait(getDriver(), 30);
    ExpectedCondition expectedCondition = new ExpectedCondition<Boolean>() {
      public Boolean apply(WebDriver d) {
        return webElement.isDisplayed();
      }
    };
    waitForWebElement.until(expectedCondition);
    return webElement;
  }

  /**
   * reportFailure: will make a list of errors and report them via the log (at some point).
   *
   * @param string
   */
  public void reportFailure(String string) {
    string = string.replace("'", "");  // Single quote will cause an SQL exception - remove
    synchronized (errorList) {
      errorList.add(string);
    }
  }

  /**
   * refresh: will refresh the page and wait for it to reload
   */
  public void refresh() {
    getDriver().navigate().refresh();

    // configuration to remember log messages so when a test fails we can output our log into the dom.
//    try {
//      executeJavascript("if(typeof() !== 'undefined') { keepLogs(true); }");
//    } catch (Exception e) {
//      if (e instanceof WebDriverException && e.toString().contains("null (WARNING: The server did not provide any stacktrace information)")) {
//        LOG.warn("Failed to execute keepLogs, seemingly due to not being able to communicate with the browser for some reason (e.g. network issue, browser crash)", e);
//      } else {
//        LOG.warn("Failed to execute keepLogs", e);
//      }
//      // This failure is ignorable since only used to diagnose test failures.
//    }
    waitForJQueryToBeInactive(getDriver());
  }

  /**
   * getCurrentUrl: returns the current url
   *
   * @param driver remote or local webDriver
   * @return the current URL
   */
  public String getCurrentUrl(WebDriver driver) {
    return driver.getCurrentUrl();
  }

  /**
   * quitBrowser: quits the web browser
   *
   * @param driver remote or local webDriver
   */
  public static void quitBrowser(WebDriver driver) {
    driver.quit();
  }

  /**
   * deleteCookies: deletes all cookies the browser is using.
   *
   * @param driver remote or local webDriver
   * @throws Exception
   */
  public void deleteCookies(WebDriver driver) throws Exception {
    try {
      driver.manage().deleteAllCookies();
    } catch (Exception e) {
      String message = "Could not delete cookies.";
      throw new Exception(message);
    }
  }

  /**
   * browserBack: simulates a user clicking the browsers back button
   *
   * @param driver remote or local webDriver
   */
  public void browserBack(WebDriver driver) {
    driver.navigate().back();
  }

  /**
   * executeJavascript: is our JS executor, send it a string (the javascript)
   *
   * @param script the CSS selector
   * @return the return of the Execution of the JS
   */
  protected Object executeJavascript(String script) {
    return executeJavascript(script, getDriver());
  }

  /**
   * getDateTime: returns the current date in the format specified.
   *
   * eg. "yyyy/MM/dd HH:mm"
   *
   * @param dateformat the requested dataFormat
   * @return properly formatted date and time.
   */
  public String getDateTime(String dateformat) {
    DateFormat dateFormat = new SimpleDateFormat(dateformat);
    Date date = new Date();
    return dateFormat.format(date);
  }

  /**
   * launchURL: will navigate to a web address
   *
   * @param driver remote or local webDriver
   * @param url destination URL
   * @throws Exception
   */
  public void launchURL(WebDriver driver, String url) throws Exception {
    try {
      driver.navigate().to(url);
    } catch (Exception e) {
      String message = "driver.navigate.to(Url) could not navigate the url: " + url;
      throw new Exception(message + " : " + e);
    }
  }

  /**
   * assertTextPresent: takes a variable number of strings and asserts that each exists on the
   * current page.
   *
   * Checks for the presence of all strings passed in and asserts that they
   * exist.
   *
   * @param driver remote or local webDriver
   * @param strList A list of strings to check on the current page.
   */
  @SuppressWarnings("SleepWhileHoldingLock")
  public void assertTextPresent(WebDriver driver, String... strList) {
    long slice = WAIT_TIME / 10;  //Will try 10 times within timeout limit
    ArrayList<String> notFoundList = new ArrayList<String>();

    try { //InterruptedException will stop the full wait loop
      for (long i = 0; i
          < WAIT_TIME; i += slice) {
        boolean found = true;

        notFoundList.clear();
        for (String s : strList) {
          if (!driver.findElement(By.tagName("body")).getText().contains(s)) {
            found = false;
            notFoundList.add(s);
          }
        }
        if (found) {
          return;
        }
        Thread.sleep(slice);
      }
    } catch (InterruptedException iex) {
      //Sleepus Interruptus
    }
    //assertTrue(notFoundList.isEmpty(), "[assertTextPresent] Expected text not found: " + notFoundList);
  }

  /**
   * assertTextNotPresent: takes a variable number of strings and asserts that each does not exist
   * on the current page.
   *
   * Checks for the presence of all strings passed in and asserts that they do
   * not exist.
   *
   * @param driver remote or local webDriver
   * @param strList A list of strings to check on the current page.
   */
  @SuppressWarnings("SleepWhileHoldingLock")
  public void assertTextNotPresent(WebDriver driver, String... strList) {
    long slice = WAIT_TIME / 10;  //Will try 10 times within getTimeoutMS() limit
    ArrayList<String> foundList = new ArrayList<String>();

    try { //InterruptedException will stop the full wait loop
      for (int i = 0; i
          < WAIT_TIME; i += slice) {
        boolean found = false;

        foundList.clear();
        for (String s : strList) {
          if (driver.findElement(By.tagName("body")).getText().contains(s)) {
            found = true;
            foundList.add(s);
          }
        }
        if (!found) {
          return;
        }
        Thread.sleep(slice);
      }
    } catch (InterruptedException iex) {
      //Sleepus Interruptus
    }
    //assertTrue(foundList.isEmpty(), "[assertTextNotPresent] Unexpected text found: " + foundList);
  }

  /**
   * isTextPresent: searches for text present on web page via the Page Source
   *
   * @param driver remote or local webDriver
   * @param stringList the list of strings to find
   * @return boolean -- TRUE or FALSE
   */
  public boolean isTextPresent(WebDriver driver, String... stringList) {
    boolean found = false;
    try {
      for (String string : stringList) {
        if (driver.findElement(By.tagName("body")).getText().contains(string)) {
          found = true;
          break;
        }
      }
      return (found);
    } catch (java.util.NoSuchElementException e) {
      return false;
    }
  }

  /**
   * verifyElementText: verifies the text of an element with the expected text passed in
   *
   * @param webElement the web element
   * @param expectedText the text you are looking for
   */
  public void verifyElementText(WebElement webElement, String expectedText) {
    String s = webElement.getText();
    s.equalsIgnoreCase(expectedText);
  }

  /**
   * implicitlyWait: is a way to tell WebDriver to poll the DOM for a certain
   * amount of time when trying to find an element if they are not immediately
   * available.
   *
   * @param driver remote or local webDriver
   * @param webElement webElement to wait for
   * @return the WebElement waited for
   */
  public WebElement implicitlyWait(WebDriver driver, WebElement webElement) {
    driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
    WebElement myDynamicElement = webElement;
    return myDynamicElement;
  }

  /**
   * explicitWait: is a way you define to wait for a certain condition to
   * occur before proceeding further in the code.
   *
   * @param driver remote or local webDriver
   * @param webElement the webElement to wait for
   * @return the WebElement waited for
   */
  public WebElement explicitWait(WebDriver driver, WebElement webElement) {
    WebElement myDynamicElement = (new WebDriverWait(driver, 10)).until(new ExpectedCondition<WebElement>() {

      @Override
      public WebElement apply(WebDriver d) {
        return d.findElement(By.id("myDynamicElement"));
      }
    });
    return myDynamicElement;
  }

  /**
   * getHREFattribute: returns the parts of the HREF in the HTML
   *
   * @param webElement the element to find the HREF attribute
   * @return returns the HREF attribute
   */
  public String getHREFattribute(WebElement webElement) {
    String hrefAttribute = webElement.getAttribute("href");
    //String[] hrefParts = hrefAttribute.split("/");

    return hrefAttribute;
  }

  /**
   * waitForJQueryToBeInactive: will wait untill all JQuery activity to finish on any given web page
   *
   * @param driver remote or local webDriver
   */
  public void waitForJQueryToBeInactive(WebDriver driver) {
    long start = System.currentTimeMillis();
    int timeOutInSeconds = 45;
    boolean result = true;
    TimeoutException timeout = new TimeoutException();
    WebDriverWait wait = new WebDriverWait(driver, timeOutInSeconds);
    try {
      wait.until(new Predicate<WebDriver>() {
        @Override
        public boolean apply(WebDriver driver) {
          return findJQueryActive();
        }
      });
      result = true;
    } catch (TimeoutException e) {
      LOG.fatal("After waiting for " + timeOutInSeconds + ", JQuery was still active, retrying...");
      driver.navigate().refresh();
      try {
        wait.until(new Predicate<WebDriver>() {
          @Override
          public boolean apply(WebDriver driver) {
            return findJQueryActive();
          }
        });
        result = true;
      } catch (TimeoutException ex) {
        LOG.fatal("After waiting for another " + timeOutInSeconds + ", JQuery was still active, failing...");
        timeout = ex;
        result = false;
      }
    }

    if (!result) {
      throw new TimeoutException("We waited for JQuery to be inactive, but received the following error instead: ", timeout);
    }

    long end = System.currentTimeMillis();
    LOG.debug("Waited for " + (end - start) + "ms waiting for JQuery to be inactive.");
  }

  /**
   * findJQueryActive: part of the above that waits to know if a page is done requesting data and is loaded.
   *
   * @return boolean if it finds JQuery active or not
   */
  private boolean findJQueryActive() {
    try {
      int active = Integer.parseInt(executeJavascript("return jQuery.active", getDriver()).toString());
      if (active != 0) {
        LOG.debug("active=" + active);
      }
      return active == 0;
    } catch (RuntimeException e) {
      LOG.info("Ignoring that jQuery.active failed.  jQuery is probably not used for the page. " + e);
      return true;
    }
  }

  /**
   * clickMouse: a way to use Javascript/JQuery to execute a MOUSE CLICK
   *
   * @param selector the CSS selector
   */
  private void clickMouse(String selector) {
    executeJavascript("$j('" + selector + "').eq(0).mousedown().mouseup().click();");
  }

  /**
   * moveMouse: a way to use Javascript/JQuery to move a mouse
   *
   * @param selector the CSS selector
   */
  public void moveMouse(String selector) {
    executeJavascript("jQuery('" + selector + "').mouseover();", getDriver());
  }

  /**
   * mouseTrigger: Activates a HOVER, where the .hover method does not.
   *
   * @param selector the CSS selector
   */
  public void mouseTrigger(String selector) {
    executeJavascript("jQuery('" + selector + "').trigger('mouseenter')");
//    executeJavascript("jQuery('" + locator + "').hover()");
  }
}
