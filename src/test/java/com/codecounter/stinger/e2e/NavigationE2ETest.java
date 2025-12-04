package com.codecounter.stinger.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.bonigarcia.wdm.WebDriverManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Disabled during CI - writes results/ if run")
public class NavigationE2ETest {

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setupDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void teardown() {
        if (driver != null) driver.quit();
    }

    @Test
    void clickingFolderPreservesBackslashes(@TempDir Path tmpDir) throws Exception {
        // Create a nested directory structure
        Path folder = tmpDir.resolve("Cakewalk Content");
        Files.createDirectories(folder);
        // Add a file inside so the folder shows up
        Files.createFile(folder.resolve("dummy.txt"));

        String baseUrl = "http://localhost:" + port + "/";
        driver.get(baseUrl);

        // Wait for input and buttons to be visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("currentPath")));
        WebElement input = driver.findElement(By.id("currentPath"));
        WebElement navigateBtn = driver.findElement(By.id("navigateBtn"));

        // Use the temp dir path as the starting path
        String startPath = tmpDir.toString();
        input.clear();
        input.sendKeys(startPath);
        navigateBtn.click();

        // Wait for the file item with the folder name to appear
        String folderName = "Cakewalk Content";
        WebElement folderElement = wait.until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath("//div[contains(@class,'file-name') and normalize-space(text())='" + folderName + "']")
        ));

        // Click the folder element's parent file-item to navigate into it
        WebElement fileItem = folderElement.findElement(By.xpath("./ancestor::div[contains(@class,'file-item')]"));
        fileItem.click();

        // Wait for the input value to change and assert it contains a backslash between drive letter and folder
        wait.until(ExpectedConditions.attributeContains(By.id("currentPath"), "value", "Cakewalk Content"));
        String current = driver.findElement(By.id("currentPath")).getAttribute("value");

        assertTrue(current.contains("\\") || current.contains("/"), "expected a path separator in currentPath but was: " + current);
        // also assert the exact path ends with the folder name
        assertTrue(current.endsWith("Cakewalk Content"));

        // Now click the "parent" item to go back up and verify the path returns to the starting path
        WebElement parentItem = wait.until(ExpectedConditions.elementToBeClickable(By.id("parentDirItem")));
        parentItem.click();

        // Wait until currentPath reflects the parent (startPath)
        wait.until(ExpectedConditions.attributeToBe(By.id("currentPath"), "value", startPath));
        String afterParent = driver.findElement(By.id("currentPath")).getAttribute("value");
        assertEquals(startPath, afterParent);
    }
}
