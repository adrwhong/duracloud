/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.durareport.rest;

import org.duracloud.durareport.service.ServiceReportBuilder;
import org.duracloud.servicemonitor.ServiceMonitor;
import org.duracloud.servicemonitor.ServiceSummarizer;
import org.duracloud.servicemonitor.ServiceSummaryDirectory;
import org.duracloud.servicemonitor.error.ServiceSummaryNotFoundException;

import java.io.InputStream;

/**
 * @author: Bill Branan
 * Date: 5/12/11
 */
public class ServiceReportResource {

    private ServiceReportBuilder reportBuilder;
    private ServiceMonitor serviceMonitor;

    public ServiceReportResource(ServiceMonitor serviceMonitor) {
        this.serviceMonitor = serviceMonitor;
    }

    public void initialize(ServiceSummaryDirectory summaryDirectory,
                           ServiceSummarizer summarizer) {
        this.serviceMonitor.initialize(summaryDirectory, summarizer);

        // Note: ReportBuilder could be an injected Spring-bean initialized here.
        this.reportBuilder = new ServiceReportBuilder(summarizer,
                                                      summaryDirectory);
    }

    /**
     * Indicates whether or not initialization has occurred.
     */
    public boolean isInitialized() {
        try {
            checkInitialized();
            return true;
        } catch(RuntimeException e) {
            return false;
        }
    }

    public InputStream getDeployedServicesReport(){
        checkInitialized();
        return reportBuilder.getDeployedServicesReport();
    }

    public InputStream getCompletedServicesReport(int limit) {
        return reportBuilder.getCompletedServicesReport(limit);
    }

    public InputStream getCompletedServicesReportList() {
        return reportBuilder.getCompletedServicesReportList();
    }

    public InputStream getCompletedServicesReport(String reportId)
        throws ServiceSummaryNotFoundException {
        return reportBuilder.getCompletedServicesReport(reportId);
    }

    public void checkInitialized() {
        if(null == reportBuilder) {
            throw new RuntimeException("DuraReport must be initialized.");
        }
    }

}
