package org.openelisglobal.reports.controller;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.form.BaseForm;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.IReportTrackingService;
import org.openelisglobal.common.services.ReportTrackingService.ReportType;
import org.openelisglobal.reports.action.implementation.IReportCreator;
import org.openelisglobal.reports.action.implementation.IReportParameterSetter;
import org.openelisglobal.reports.action.implementation.ReportImplementationFactory;
import org.openelisglobal.reports.form.ReportForm;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@SessionAttributes("form")
public class ReportController extends BaseController {

    @Autowired
    ServletContext context;

    @Autowired
    private SiteInformationService siteInformationService;

    private static String reportPath = null;
    private static String imagesPath = null;

    @ModelAttribute("form")
    public BaseForm form() {
        return new ReportForm();
    }

    @RequestMapping(value = "/Report", method = RequestMethod.GET)
    public ModelAndView showReport(HttpServletRequest request, @ModelAttribute("form") BaseForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        form = resetFormToType(form, ReportForm.class);
        form.setFormMethod(RequestMethod.GET);

        PropertyUtils.setProperty(form, "type", request.getParameter("type"));
        PropertyUtils.setProperty(form, "report", request.getParameter("report"));
        IReportParameterSetter setter = ReportImplementationFactory.getParameterSetter(request.getParameter("report"));

        if (setter != null) {
            setter.setRequestParameters(form);
        }

        return findForward(FWD_SUCCESS, form);
    }

    @RequestMapping(value = "/ReportPrint", method = RequestMethod.GET)
    public ModelAndView showReportPrint(HttpServletRequest request, HttpServletResponse response,
            @ModelAttribute("form") @Valid ReportForm form, BindingResult result, SessionStatus status)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        if (result.hasErrors()) {
            saveErrors(result);
            return findForward(FWD_FAIL, form);
        }

        PropertyUtils.setProperty(form, "type", request.getParameter("type"));

        IReportCreator reportCreator = ReportImplementationFactory.getReportCreator(request.getParameter("report"));

        if (reportCreator != null) {
            reportCreator.setRequestedReport(request.getParameter("report"));
            reportCreator.initializeReport(form);
            reportCreator.setReportPath(getReportPath());

            HashMap<String, String> parameterMap = (HashMap<String, String>) reportCreator.getReportParameters();
            parameterMap.put("SUBREPORT_DIR", getReportPath());
            parameterMap.put("imagesPath", getImagesPath());

            try {
                response.setContentType(reportCreator.getContentType());
                String responseHeaderName = reportCreator.getResponseHeaderName();
                String responseHeaderContent = reportCreator.getResponseHeaderContent();
                if (!GenericValidator.isBlankOrNull(responseHeaderName)
                        && !GenericValidator.isBlankOrNull(responseHeaderContent)) {
                    response.setHeader(responseHeaderName, responseHeaderContent);
                }

                byte[] bytes = reportCreator.runReport();

                response.setContentLength(bytes.length);

                ServletOutputStream servletOutputStream = response.getOutputStream();

                servletOutputStream.write(bytes, 0, bytes.length);
                servletOutputStream.flush();
                servletOutputStream.close();
            } catch (Exception e) {
                LogEvent.logErrorStack(this.getClass().getSimpleName(), "showReportPrint", e);
                LogEvent.logDebug(e);
            }
        }

        if ("patient".equals(request.getParameter("type"))) {
            trackReports(reportCreator, request.getParameter("report"), ReportType.PATIENT);
        }

        // signal to remove from from session
        status.setComplete();
        return null;
    }

    private void trackReports(IReportCreator reportCreator, String reportName, ReportType type) {
        List<String> refIds = reportCreator.getReportedOrders() != null ? reportCreator.getReportedOrders()
                : new ArrayList<>();
        SpringContext.getBean(IReportTrackingService.class).addReports(refIds, type, reportName, getSysUserId(request));
    }

    public String getReportPath() {

        if (reportPath == null) {
            SiteInformation reportsPath = siteInformationService.getSiteInformationByName("reportsDirectory");
            if (reportsPath != null) {
                reportPath = reportsPath.getValue();
                return reportPath;
            }
            ClassLoader classLoader = getClass().getClassLoader();
            reportPath = classLoader.getResource("reports").getPath();
            try {
                reportPath = URLDecoder.decode(reportPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LogEvent.logDebug(e);
                throw new LIMSRuntimeException(e);
            }
        }
        return reportPath;
    }

    public String getImagesPath() {
        if (imagesPath == null) {
            imagesPath = context.getRealPath("") + "static" + File.separator + "images" + File.separator;
            try {
                imagesPath = URLDecoder.decode(imagesPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LogEvent.logDebug(e);
                throw new LIMSRuntimeException(e);
            }
        }
        return imagesPath;
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "commonReportDefiniton";
        } else if (FWD_FAIL.equals(forward)) {
            return "commonReportDefiniton";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String getPageSubtitleKey() {
        return "reports.add.params";
    }

    @Override
    protected String getPageTitleKey() {
        return "reports.add.params";
    }

}
