/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.obs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openmrs.GlobalProperty;
import org.openmrs.Obs;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.obs.handler.AbstractHandler;
import org.openmrs.test.jupiter.BaseContextSensitiveTest;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.beans.factory.annotation.Autowired;

public class AbstractHandlerTest extends BaseContextSensitiveTest {
	
	private final String FILENAME = "mytxtfile.txt";
	
	private  AbstractHandler handler;
	
	@Autowired
	private AdministrationService adminService;

	@TempDir
	public Path complexObsTestFolder;
	
	@BeforeEach
	public void initializeContext() throws APIException, IOException {
		handler = new  AbstractHandler();
		adminService.saveGlobalProperty(new GlobalProperty(
			OpenmrsConstants.GLOBAL_PROPERTY_COMPLEX_OBS_DIR,
			complexObsTestFolder.toAbsolutePath().toString()
		));
	}
	
	@Test
	public void getOutputFileToWrite_shouldNeverOverwritePreviousFiles() throws IOException {
		String content1 = "A";
		String content2 = "B";
		
		File previousFile = null;
		File currentFile = null;
		
		for (int i = 0; i <= 101; i++) {
			String currentData = (i % 2 == 0) ? content1 : content2;
			
			ComplexData complexData = new ComplexData(FILENAME, currentData);
			
			Obs obs = new Obs();
			obs.setComplexData(complexData);
			
			currentFile = handler.getOutputFileToWrite(obs);
			
			try (BufferedWriter fout = new BufferedWriter(
			        new OutputStreamWriter(new FileOutputStream(currentFile), StandardCharsets.UTF_8))) {
				fout.write(currentData);
			}
			
			try (BufferedReader fin = new BufferedReader(
			        new InputStreamReader(new FileInputStream(currentFile), StandardCharsets.UTF_8))) {
				String readData = fin.readLine();
				assertEquals(readData, currentData);
			}
			
			if (i > 0) {
				assertFalse(FileUtils.contentEquals(previousFile, currentFile));
			}
			
			previousFile = currentFile;
		}
	}
	
	@Test
	public void getOutputFileToWrite_shouldCorrectlyNameTitledFileWithExtension() throws IOException, ParseException {
		ComplexData complexDataWithTitle = new ComplexData(FILENAME, null);
		
		Obs obsWithTitle = new Obs();
		obsWithTitle.setComplexData(complexDataWithTitle);
		
		File titledFile = handler.getOutputFileToWrite(obsWithTitle);
		titledFile.createNewFile();
		
		String[] nameWithTitle = titledFile.getName().split("_|\\.");
		
		String titlePart = nameWithTitle[0];
		String uuidPartWithTitle = nameWithTitle[1];
		String extensionPart = nameWithTitle[2];
		
		assertEquals(titlePart, FilenameUtils.removeExtension(FILENAME));
		assertEquals(extensionPart, "txt");
		assertEquals(uuidPartWithTitle, obsWithTitle.getUuid());
	}
	
	@Test
	public void getOutputFileToWrite_shouldCorrectlyNameTitledFileWithoutExtension() throws IOException, ParseException {
		ComplexData complexDataWithoutExtension = new ComplexData(FilenameUtils.removeExtension(FILENAME), null);
		
		Obs obsWithoutExtension = new Obs();
		obsWithoutExtension.setComplexData(complexDataWithoutExtension);
		
		File extensionlessFile = handler.getOutputFileToWrite(obsWithoutExtension);
		extensionlessFile.createNewFile();
		
		String[] nameWithoutExtension = extensionlessFile.getName().split("_|\\.");
		
		String titlePartExtensionless = nameWithoutExtension[0];
		String uuidPartExtensionless = nameWithoutExtension[1];
		String extensionPartExtensionless = nameWithoutExtension[2];
		
		assertEquals(titlePartExtensionless, FilenameUtils.removeExtension(FILENAME));
		assertEquals(extensionPartExtensionless, "dat");
		assertEquals(uuidPartExtensionless, obsWithoutExtension.getUuid());
	}
	
	@Test
	public void getOutputFileToWrite_shouldCorrectlyNameNullTitledFile() throws IOException, ParseException {
		ComplexData complexDataWithNullTitle = new ComplexData(null, null);
		
		Obs obsWithNullTitle = new Obs();
		obsWithNullTitle.setComplexData(complexDataWithNullTitle);
		
		File nullTitleFile = handler.getOutputFileToWrite(obsWithNullTitle);
		nullTitleFile.createNewFile();
		
		String[] nameWithNullTitle = nullTitleFile.getName().split("\\.");
		
		String uuidPartWithNullTitle = nameWithNullTitle[0];
		String extensionPartWithNullTitle = nameWithNullTitle[1];
		
		assertEquals(extensionPartWithNullTitle, "dat");
		assertEquals(uuidPartWithNullTitle, obsWithNullTitle.getUuid());
	}
	
}
