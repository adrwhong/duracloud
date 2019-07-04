/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.notification;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.validator.routines.EmailValidator;
import org.duracloud.common.error.DuraCloudRuntimeException;
import org.duracloud.notification.SpringEmailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrew Woods
 * Date: 3/11/11
 */
public class SpringNotificationFactory implements NotificationFactory {

    private static final Logger log = LoggerFactory.getLogger(
        SpringNotificationFactory.class);

    private SpringEmailer emailer;
    private Map<String, Emailer> emailerMap = new HashMap<String, Emailer>();

    public void initialize(String username, String password, String host, String port) {
        emailer = new SpringEmailer(username, password, host, port);
    }

    @Override
    public Emailer getEmailer(String fromAddress) {
        if (null == fromAddress ||
            !EmailValidator.getInstance().isValid(fromAddress)) {
            String msg = "fromAddress not valid notification: " + fromAddress;
            log.error(msg);
            throw new IllegalArgumentException(msg);
        }

        if (null == emailService) {
            String msg = "Emailer service !initialized.";
            log.error(msg);
            throw new DuraCloudRuntimeException(msg);
        }

        Emailer emailer = emailerMap.get(fromAddress);
        if (null == emailer) {
            emailer = new SpringEmailer(fromAddress); ////!!!!!!!!
            emailerMap.put(fromAddress, emailer);
        }

        return emailer;
    }

}
