package org.openelisglobal.reports.action.implementation;

import java.io.ByteArrayInputStream;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.address.service.AddressPartService;
import org.openelisglobal.address.service.PersonAddressService;
import org.openelisglobal.address.valueholder.AddressPart;
import org.openelisglobal.address.valueholder.PersonAddress;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.formfields.FormFields;
import org.openelisglobal.common.formfields.FormFields.Field;
import org.openelisglobal.common.services.TableIdService;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.image.service.ImageService;
import org.openelisglobal.image.valueholder.Image;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.observationhistory.service.ObservationHistoryServiceImpl.ObservationType;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.service.PatientServiceImpl;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.program.service.PathologyDisplayService;
import org.openelisglobal.program.service.PathologySampleService;
import org.openelisglobal.program.valueholder.pathology.PathologyConclusion;
import org.openelisglobal.program.valueholder.pathology.PathologyConclusion.ConclusionType;
import org.openelisglobal.program.valueholder.pathology.PathologySample;
import org.openelisglobal.program.valueholder.pathology.PathologyTechnique.TechniqueType;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.reports.action.implementation.reportBeans.ClinicalPatientData;
import org.openelisglobal.reports.action.implementation.reportBeans.ProgramSampleReportData;
import org.openelisglobal.reports.form.ReportForm;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sample.valueholder.SampleAdditionalField.AdditionalFieldName;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestServiceImpl;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

public abstract class PatientProgramReport  extends Report implements IReportCreator {
    private ImageService imageService = SpringContext.getBean(ImageService.class);
    protected PathologySampleService pathologySerivice = SpringContext.getBean(PathologySampleService.class);
    protected PatientService patientService = SpringContext.getBean(PatientService.class);
    private static final String configName = ConfigurationProperties.getInstance().getPropertyValue(Property.configurationName);
    protected SampleService sampleService = SpringContext.getBean(SampleService.class);
    protected SampleHumanService sampleHumanService = SpringContext.getBean(SampleHumanService.class);
    protected AddressPartService addressPartService = SpringContext.getBean(AddressPartService.class);
    protected OrganizationService organizationService = SpringContext.getBean(OrganizationService.class);
    protected DictionaryService dictionaryService = SpringContext.getBean(DictionaryService.class);
    protected PersonAddressService addressService = SpringContext.getBean(PersonAddressService.class);
    protected ProviderService providerService = SpringContext.getBean(ProviderService.class);
    protected AnalysisService analysisService = SpringContext.getBean(AnalysisService.class);
    private static String ADDRESS_DEPT_ID;
    private static String ADDRESS_COMMUNE_ID;
    protected String currentContactInfo = "";
    protected String currentSiteInfo = ""; 
    protected String STNumber = null;
    protected String subjectNumber = null;
    protected String healthRegion = null;
    protected String healthDistrict = null;
    protected String patientName = null;
    protected String patientDOB = null;
    protected String currentConclusion = null;

    protected String patientDept = null;
    protected String patientCommune = null;

    protected Provider currentProvider;
    protected Analysis currentAnalysis;
    protected String reportReferralResultValue;
    protected String completionDate;
    protected Patient patient;
    protected Sample sample ;
    protected List<ProgramSampleReportData> reportItems;
    protected List<Analysis> analyses;
    protected  ProgramSampleReportData data;

    abstract protected String getReportName();

    abstract protected void setAdditionalReportItems();

    abstract protected void innitializeSample(ReportForm form);

    @Override
    protected String reportFileName() {
        return getReportName();
    }

    protected String getHeaderName() {
        return "CDIHeader.jasper";
    }

    @PostConstruct
    private void initialize() {
        List<AddressPart> partList = addressPartService.getAll();
        for (AddressPart part : partList) {
            if ("department".equals(part.getPartName())) {
                ADDRESS_DEPT_ID = part.getId();
            } else if ("commune".equals(part.getPartName())) {
                ADDRESS_COMMUNE_ID = part.getId();
            }
        }
    }

    @Override
    public void initializeReport(ReportForm form) {
        super.initializeReport();
        errorFound = false;
        createReportParameters();
        innitializeSample(form);
        currentAnalysis = analysisService.getAnalysesBySampleId(sample.getId()).get(0);
        analyses = analysisService.getAnalysesBySampleId(sample.getId());
        initializeReportItems();
        findCompletionDate();
        findPatientFromSample();
        findContactInfo();
        findPatientInfo();
        createReportItems();
    }

    protected void createReportItems() {
        reportItems.add(buildClinicalPatientData());
    }
    @Override
    public JRDataSource getReportDataSource() throws IllegalStateException {
        if (!initialized) {
            throw new IllegalStateException("initializeReport not called first");
        }

        return errorFound ? new JRBeanCollectionDataSource(errorMsgs) : new JRBeanCollectionDataSource(reportItems);
    }

    @Override
    protected void createReportParameters() {
        super.createReportParameters();
        reportParameters.put("siteId", ConfigurationProperties.getInstance().getPropertyValue(Property.SiteCode));
        reportParameters.put("headerName", getHeaderName());
        reportParameters.put("billingNumberLabel",
                SpringContext.getBean(LocalizationService.class).getLocalizedValueById(ConfigurationProperties
                        .getInstance().getPropertyValue(Property.BILLING_REFERENCE_NUMBER_LABEL)));
        reportParameters.put("footerName", getFooterName());
        Optional<Image> labDirectorSignature = imageService.getImageBySiteInfoName("labDirectorSignature");
        reportParameters.put("useLabDirectorSignature", labDirectorSignature.isPresent());
        if (labDirectorSignature.isPresent()) {
            reportParameters.put("labDirectorSignature",
                    new ByteArrayInputStream(labDirectorSignature.get().getImage()));
        }

        reportParameters.put("labDirectorName",
                ConfigurationProperties.getInstance().getPropertyValue(Property.LAB_DIRECTOR_NAME));
        reportParameters.put("labDirectorTitle",
                ConfigurationProperties.getInstance().getPropertyValue(Property.LAB_DIRECTOR_TITLE));
    }

    private Object getFooterName() {
        if (configName.equals("CI IPCI") || configName.equals("CI LNSP")) {
            return "CILNSPFooter.jasper";
        } else {
            return "";
        }
    }

    protected void initializeReportItems() {
        reportItems = new ArrayList<>();
    }

     private void findCompletionDate() {
        Date date = sampleService.getCompletedDate(sample);
        completionDate = date == null ? null : DateUtil.convertSqlDateToStringDate(date);
    }

    protected void findPatientFromSample() {
        patient = sampleHumanService.getPatientForSample(sample);
    }

     private void findPatientInfo() {
        if (patientService.getPerson(patient) == null) {
            return;
        }

        patientDept = "";
        patientCommune = "";
        if (ADDRESS_DEPT_ID != null) {
            PersonAddress deptAddress = addressService
                    .getByPersonIdAndPartId(patientService.getPerson(patient).getId(), ADDRESS_DEPT_ID);

            if (deptAddress != null && !GenericValidator.isBlankOrNull(deptAddress.getValue())) {
                patientDept = dictionaryService.getDictionaryById(deptAddress.getValue()).getDictEntry();
            }
        }

        if (ADDRESS_COMMUNE_ID != null) {
            PersonAddress deptAddress = addressService
                    .getByPersonIdAndPartId(patientService.getPerson(patient).getId(), ADDRESS_COMMUNE_ID);

            if (deptAddress != null) {
                patientCommune = deptAddress.getValue();
            }
        }

    }

     private void findContactInfo() {
        currentContactInfo = "";
        currentSiteInfo = "";
        currentProvider = null;

//        sampleService.getOrganizationRequester(currentSample);
        Organization referringOrg = sampleService.getOrganizationRequester(sample,
                TableIdService.getInstance().REFERRING_ORG_TYPE_ID);
        Organization referringDepartmentOrg = sampleService.getOrganizationRequester(sample,
                TableIdService.getInstance().REFERRING_ORG_DEPARTMENT_TYPE_ID);

        currentSiteInfo += referringOrg == null ? "" : referringOrg.getOrganizationName();
        currentSiteInfo += "|" + (referringDepartmentOrg == null ? "" : referringDepartmentOrg.getOrganizationName());

        Person person = sampleService.getPersonRequester(sample);
        if (person != null) {
            PersonService personService = SpringContext.getBean(PersonService.class);
            currentContactInfo = personService.getLastFirstName(person);
            currentProvider = providerService.getProviderByPerson(person);
        }

    }

    protected ProgramSampleReportData buildClinicalPatientData() {
        data = new ProgramSampleReportData();
        String testName = TestServiceImpl.getUserLocalizedTestName(analysisService.getTest(currentAnalysis));;
        String sortOrder = "";
        String receivedDate = sampleService.getReceivedDateForDisplay(sample);
        Timestamp orderDate = sampleService.getOrderedDate(sample);
        String orderDateForDisplay = orderDate != null ? DateUtil.convertTimestampToStringDateAndConfiguredHourTime(orderDate) : "";


        if (FormFields.getInstance().useField(Field.SampleEntryUseReceptionHour)) {
            receivedDate += " " + sampleService.getReceivedTimeForDisplay(sample);
        }
        ObservationHistoryService observationHistoryService = SpringContext.getBean(ObservationHistoryService.class);
        data.setSampleType(analysisService.getTypeOfSample(currentAnalysis).getLocalizedName());
        Set<SampleItem> sampleSet = new HashSet<>();
        analyses.forEach(analysis -> {sampleSet.add(analysis.getSampleItem());});
        setCollectionTime(sampleSet, data, true);
        data.setContactInfo(currentContactInfo);
        data.setSiteInfo(currentSiteInfo);
        data.setReceivedDate(receivedDate);
        data.setDob(getPatientDOB(patient));
        data.setAge(createReadableAge(data.getDob()));
        data.setGender(patientService.getGender(patient));
        data.setNationalId(patientService.getNationalId(patient));
        setPatientName(data);
        data.setDept(patientDept);
        data.setCommune(patientCommune);
        data.setStNumber(getLazyPatientIdentity(patient, STNumber, PatientServiceImpl.getPatientSTIdentity()));
        data.setSubjectNumber(
                getLazyPatientIdentity(patient, subjectNumber, PatientServiceImpl.getPatientSubjectIdentity()));
        data.setHealthRegion(getLazyPatientIdentity(patient, healthRegion,
                PatientServiceImpl.getPatientHealthRegionIdentity()));
        data.setHealthDistrict(getLazyPatientIdentity(patient, healthDistrict,
                PatientServiceImpl.getPatientHealthDistrictIdentity()));

        data.setLabOrderType(observationHistoryService.getValueForSample(ObservationType.PROGRAM,
                sampleService.getId(sample)));
        data.setTestName(testName);
        data.setPatientSiteNumber(observationHistoryService.getValueForSample(ObservationType.REFERRERS_PATIENT_ID,
                sampleService.getId(sample)));
        data.setBillingNumber(observationHistoryService.getValueForSample(ObservationType.BILLING_REFERENCE_NUMBER,
                sampleService.getId(sample)));
        data.setOrderDate(orderDateForDisplay);
        data.setSampleSortOrder(currentAnalysis.getSampleItem().getSortOrder());
        data.setSampleId(sampleService.getAccessionNumber(sample) + "-" + data.getSampleSortOrder());
        data.setAccessionNumber(sampleService.getAccessionNumber(sample) + "-" + data.getSampleSortOrder());

        if (Boolean.valueOf(ConfigurationProperties.getInstance().getPropertyValue(Property.CONTACT_TRACING))) {
            data.setContactTracingIndexName(
                    sampleService.getSampleAdditionalFieldForSample(sampleService.getId(sample),
                            AdditionalFieldName.CONTACT_TRACING_INDEX_NAME).getFieldValue());
            data.setContactTracingIndexRecordNumber(
                    sampleService.getSampleAdditionalFieldForSample(sampleService.getId(sample),
                            AdditionalFieldName.CONTACT_TRACING_INDEX_RECORD_NUMBER).getFieldValue());
        }
        setAdditionalReportItems();
        return data;
    }

     protected void setCollectionTime(Set<SampleItem> sampleSet, ProgramSampleReportData data,
            boolean addAccessionNumber) {
        StringBuilder buffer = new StringBuilder();
        boolean firstItem = true;
        for (SampleItem sampleItem : sampleSet) {
            if (firstItem) {
                firstItem = false;
            } else {
                buffer.append(", ");
            }

            buffer.append(sampleItem.getTypeOfSample().getLocalizedName());
            if (addAccessionNumber) {
                buffer.append(" ");
                buffer.append(sampleItem.getSample().getAccessionNumber() + "-" + sampleItem.getSortOrder());
            }
            if (sampleItem.getCollectionDate() == null) {
                buffer.append(" -- ");
                buffer.append(MessageUtil.getMessage("label.not.available"));
            } else {
                buffer.append(" ");
                buffer.append(
                        DateUtil.convertTimestampToStringDateAndConfiguredHourTime(sampleItem.getCollectionDate()));
            }
        }

        String collectionTimes = buffer.toString();
        
        data.setCollectionDateTime(collectionTimes);
        
    }

    private String createReadableAge(String dob) {
        if (GenericValidator.isBlankOrNull(dob)) {
            return "";
        }

        dob = dob.replaceAll(DateUtil.AMBIGUOUS_DATE_SEGMENT, "01");
        Date dobDate = DateUtil.convertStringDateToSqlDate(dob);
        int months = DateUtil.getAgeInMonths(dobDate, DateUtil.getNowAsSqlDate());
        if (months > 35) {
            return (months / 12) + " " + MessageUtil.getMessage("abbreviation.year.single");
        } else if (months > 0) {
            return months + " " + MessageUtil.getMessage("abbreviation.month.single");
        } else {
            int days = DateUtil.getAgeInDays(dobDate, DateUtil.getNowAsSqlDate());
            return days + " " + MessageUtil.getMessage("abbreviation.day.single");
        }

    }

    protected String getPatientDOB(Patient patient) {
        if (patientDOB == null) {
            patientDOB = patientService.getBirthdayForDisplay(patient);
        }

        return patientDOB;
    }

    protected void setPatientName(ProgramSampleReportData data) {
        data.setPatientName(patientService.getLastFirstName(patient));
        data.setFirstName(patientService.getFirstName(patient));
        data.setLastName(patientService.getLastName(patient));
    }

    protected String getLazyPatientIdentity(Patient patient, String identity, String id) {
        if (identity == null) {
            identity = " ";
            List<PatientIdentity> identities = patientService.getIdentityList(patient);
            for (PatientIdentity patientIdentity : identities) {
                if (patientIdentity.getIdentityTypeId().equals(id)) {
                    identity = patientIdentity.getIdentityData();
                    break;
                }
            }
        }

        return identity;
    }
    
}
