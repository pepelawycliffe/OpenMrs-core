/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.validator;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.annotation.Handler;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validator for the {@link Visit} class.
 * @since 1.9
 */
@Handler(supports = { Visit.class }, order = 50)
public class VisitValidator extends BaseCustomizableValidator implements Validator {
	
	private static final double ESTIMATED_BIRTHDATE_ERROR_MARGIN = -0.5;
	
	private static final int ESTIMATED_BIRTHDATE_ERROR_MARGIN_MINIMUM_YEARS = -1;
	
	/**
	 * @see org.springframework.validation.Validator#supports(java.lang.Class)
	 */
	@Override
	public boolean supports(Class<?> clazz) {
		return Visit.class.isAssignableFrom(clazz);
	}
	
	/**
	 * @see org.springframework.validation.Validator#validate(java.lang.Object,
	 *      org.springframework.validation.Errors)
	 * <strong>Should</strong> accept a visit that has the right number of attribute occurrences
	 * <strong>Should</strong> reject a visit if it has fewer than min occurs of an attribute
	 * <strong>Should</strong> reject a visit if it has more than max occurs of an attribute
	 * <strong>Should</strong> fail if patient is not set
	 * <strong>Should</strong> fail if visit type is not set
	 * <strong>Should</strong> fail if startDatetime is not set
	 * <strong>Should</strong> fail if the endDatetime is before the startDatetime
	 * <strong>Should</strong> fail if the startDatetime is after any encounter
	 * <strong>Should</strong> fail if the stopDatetime is before any encounter
	 * <strong>Should</strong> fail if an attribute is bad
	 *
	 * <strong>Should</strong> reject a visit if startDateTime is equal to startDateTime of another visit of the same patient
	 * <strong>Should</strong> reject a visit if startDateTime falls into another visit of the same patient
	 * <strong>Should</strong> reject a visit if stopDateTime falls into another visit of the same patient
	 * <strong>Should</strong> reject a visit if it contains another visit of the same patient

	 * <strong>Should</strong> accept a visit if startDateTime is equal to startDateTime of another voided visit of the same patient
	 * <strong>Should</strong> accept a visit if startDateTime falls into another voided visit of the same patient
	 * <strong>Should</strong> accept a visit if stopDateTime falls into another voided visit of the same patient
	 * <strong>Should</strong> accept a visit if it contains another voided visit of the same patient
	 *
	 * <strong>Should</strong> pass validation if field lengths are correct
	 * <strong>Should</strong> fail validation if field lengths are not correct
	 */
	@Override
	public void validate(Object target, Errors errors) {
		Visit visit = (Visit) target;
		ValidationUtils.rejectIfEmpty(errors, "patient", "Visit.error.patient.required");
		ValidationUtils.rejectIfEmpty(errors, "visitType", "Visit.error.visitType.required");
		ValidationUtils.rejectIfEmpty(errors, "startDatetime", "Visit.error.startDate.required");
		if (visit.getStartDatetime() != null
		        && OpenmrsUtil.compareWithNullAsLatest(visit.getStartDatetime(), visit.getStopDatetime()) > 0) {
			errors.rejectValue("stopDatetime", "Visit.error.endDateBeforeStartDate");
		}
		
		//If this is not a new visit, validate based on its existing encounters.
		if (visit.getId() != null) {
			Date startDateTime = visit.getStartDatetime();
			Date stopDateTime = visit.getStopDatetime();
			
			List<Encounter> encounters = Context.getEncounterService().getEncountersByVisit(visit, false);
			for (Encounter encounter : encounters) {
				if (encounter.getEncounterDatetime().before(startDateTime)) {
					errors.rejectValue("startDatetime", "Visit.encountersCannotBeBeforeStartDate",
					    "This visit has encounters whose dates cannot be before the start date of the visit.");
					break;
				} else if (stopDateTime != null && encounter.getEncounterDatetime().after(stopDateTime)) {
					errors.rejectValue("stopDatetime", "Visit.encountersCannotBeAfterStopDate",
					    "This visit has encounters whose dates cannot be after the stop date of the visit.");
					break;
				}
			}
		}
		
		ValidateUtil.validateFieldLengths(errors, target.getClass(), "voidReason");
		
		// check attributes
		super.validateAttributes(visit, errors, Context.getVisitService().getAllVisitAttributeTypes());
		
		// check start and end dates
		if (disallowOverlappingVisits()) {
			List<Visit> otherVisitList = Context.getVisitService().getVisitsByPatient(visit.getPatient());
			for (Visit otherVisit : otherVisitList) {
				validateStartDatetime(visit, otherVisit, errors);
				validateStopDatetime(visit, otherVisit, errors);
			}
		}
		
		validateVisitStartedBeforePatientBirthdate(visit, errors);
	}
	
	/*
	 * Convenience method to make the code more readable.
	 */
	private boolean disallowOverlappingVisits() {
		return !allowOverlappingVisits();
	}
	
	private boolean allowOverlappingVisits() {
		return Boolean.parseBoolean(Context.getAdministrationService().getGlobalProperty(
		    OpenmrsConstants.GLOBAL_PROPERTY_ALLOW_OVERLAPPING_VISITS, "true"));
	}
	
	private void validateStartDatetime(Visit visit, Visit otherVisit, Errors errors) {
		
		if (visit.getStartDatetime() != null && otherVisit.getStartDatetime() != null
		        && visit.getStartDatetime().equals(otherVisit.getStartDatetime())) {
			errors.rejectValue("startDatetime", "Visit.startCannotBeTheSameAsOtherStartDateOfTheSamePatient",
			    "This visit has the same start date and time as another visit of this patient.");
		}
		
		if (visit.getStartDatetime() != null && otherVisit.getStartDatetime() != null
		        && otherVisit.getStopDatetime() != null && visit.getStartDatetime().after(otherVisit.getStartDatetime())
		        && visit.getStartDatetime().before(otherVisit.getStopDatetime())) {
			errors.rejectValue("startDatetime", "Visit.startDateCannotFallIntoAnotherVisitOfTheSamePatient",
			    "This visit has a start date that falls into another visit of the same patient.");
		}
	}
	
	private void validateStopDatetime(Visit visit, Visit otherVisit, Errors errors) {
		
		if (visit.getStopDatetime() != null && otherVisit.getStartDatetime() != null && otherVisit.getStopDatetime() != null
		        && visit.getStopDatetime().after(otherVisit.getStartDatetime())
		        && visit.getStopDatetime().before(otherVisit.getStopDatetime())) {
			errors.rejectValue("stopDatetime", "Visit.stopDateCannotFallIntoAnotherVisitOfTheSamePatient",
			    "This visit has a stop date that falls into another visit of the same patient.");
			
		}
		
		if (visit.getStartDatetime() != null && visit.getStopDatetime() != null && otherVisit.getStartDatetime() != null
		        && otherVisit.getStopDatetime() != null && visit.getStartDatetime().before(otherVisit.getStartDatetime())
		        && visit.getStopDatetime().after(otherVisit.getStopDatetime())) {

			String message = "This visit contains another visit of the same patient, "
					+ "i.e. its start date is before the start date of the other visit "
					+ "and its stop date is after the stop date of the other visit.";

			errors.rejectValue("stopDatetime", "Visit.visitCannotContainAnotherVisitOfTheSamePatient", message);
		}
		
	}
	
	private void validateVisitStartedBeforePatientBirthdate(Visit visit, Errors errors) {
		if (visit.getPatient() == null || visit.getPatient().getBirthdate() == null || visit.getStartDatetime() == null) {
			return;
		}
		
		if (visit.getStartDatetime().before(getPatientBirthdateAdjustedIfEstimated(visit.getPatient()))) {
			errors.rejectValue("startDatetime", "Visit.startDateCannotFallBeforeTheBirthDateOfTheSamePatient",
			    "This visit has a start date that falls before the birthdate of the same patient.");
		}
	}
	
	private Date getPatientBirthdateAdjustedIfEstimated(Patient patient) {
		Date birthday = patient.getBirthdate();
		
		if (patient.getBirthdateEstimated()) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(birthday);
			cal.add(Calendar.YEAR, calculateGracePeriodInYears(patient.getAge()));
			birthday = cal.getTime();
		}
		
		return birthday;
	}
	
	private int calculateGracePeriodInYears(int age) {
		return Math.min(ESTIMATED_BIRTHDATE_ERROR_MARGIN_MINIMUM_YEARS,
			(int)Math.ceil(age * ESTIMATED_BIRTHDATE_ERROR_MARGIN));
	}
}
