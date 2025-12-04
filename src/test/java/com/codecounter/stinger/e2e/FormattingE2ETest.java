package com.codecounter.stinger.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import io.github.bonigarcia.wdm.WebDriverManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Disabled("Disabled during CI - writes code_counter_results/ if run")
public class FormattingE2ETest {

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
    void ensureFormattingButtonIsHiddenWhenNoSelection(@TempDir Path tmpDir) throws Exception {
        Path start = tmpDir.resolve("project");
        Files.createDirectories(start);
        Files.writeString(start.resolve("A.java"), "public class A {}\n");

        String baseUrl = "http://localhost:" + port + "/";
        driver.get(baseUrl);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("currentPath")));
        driver.findElement(By.id("currentPath")).sendKeys(start.toString());
        driver.findElement(By.id("navigateBtn")).click();

        // Wait for file list to appear
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.file-item")));

        // The formatting control should be hidden until a file is selected
        assertTrue(driver.findElements(By.id("formatBtn")).isEmpty() || !driver.findElement(By.id("formatBtn")).isDisplayed());
    }
}
