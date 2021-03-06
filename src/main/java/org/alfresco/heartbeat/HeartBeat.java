/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.heartbeat;

import java.util.Date;

import org.alfresco.service.cmr.repository.HBDataCollectorService;
import org.alfresco.service.license.LicenseDescriptor;
import org.alfresco.service.license.LicenseService;
import org.alfresco.service.license.LicenseService.LicenseChangeHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

/**
 * This class communicates some very basic repository statistics to Alfresco on a regular basis.
 * The class is responsible for scheduling the HeartBeat job and reacting to licence change events.
 * 
 * @author dward, eknizat
 */
public class HeartBeat implements LicenseChangeHandler
{

    /** The logger */
    private static final Log logger = LogFactory.getLog(HeartBeat.class);

    private LicenseService licenseService;

    private Scheduler scheduler;

    private boolean testMode = true;

    private final String JOB_NAME = "heartbeat";

    private HBDataCollectorService dataCollectorService;

    /** Current enabled state */
    private boolean enabled = false;

    /**
     * Initialises the heart beat service. Note that dependencies are intentionally 'pulled' rather than injected
     * because we don't want these to be reconfigured.
     *
     * @param context
     *            the context
     */
    public HeartBeat(final ApplicationContext context)
    {
        this(context, false);
    }

    /**
     * Initialises the heart beat service, potentially in test mode. Note that dependencies are intentionally 'pulled'
     * rather than injected because we don't want these to be reconfigured.
     *
     * -@param context
     *            the context
     * -@param testMode
     *            are we running in test mode? If so we send data to local port 9999 rather than an alfresco server. We
     *            also use a special test encryption certificate and ping on a more frequent basis.
     */
    public HeartBeat(final ApplicationContext context, final Boolean testMode)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Initialising HeartBeat");
        }

        this.dataCollectorService = (HBDataCollectorService) context.getBean("hbDataCollectorService");
        this.scheduler = (Scheduler) context.getBean("schedulerFactory");

        this.testMode = testMode;

        this.enabled = dataCollectorService.isEnabledByDefault();

        try
        {
            LicenseService licenseService = null;
            try
            {
                licenseService = (LicenseService) context.getBean("licenseService");
                licenseService.registerOnLicenseChange(this);
            }
            catch (NoSuchBeanDefinitionException e)
            {
                logger.error("licenseService not found", e);
            }
            this.licenseService = licenseService;

            // We force the job to be scheduled regardless of the potential state of the licenses
            scheduleJob();
        }
        catch (final RuntimeException e)
        {
            throw e;
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    public synchronized  boolean isEnabled()
    {
        return this.enabled;
    }

    /**
     *  Delegates data collection and sending to HBDataCollectorService.
     *
     */
    public void collectAndSendData()
    {
        this.dataCollectorService.collectAndSendData();
    }

    /**
     * Listens for license changes.  If a license is change or removed, the heartbeat job is rescheduled.
     */
    public synchronized void onLicenseChange(LicenseDescriptor licenseDescriptor)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Update license called");
        }

        boolean newEnabled = !licenseDescriptor.isHeartBeatDisabled();

        if (newEnabled != this.enabled)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("State change of heartbeat");
            }
            this.enabled = newEnabled;
            dataCollectorService.enabled(newEnabled);
            try
            {
                scheduleJob();
            }
            catch (Exception e)
            {
                logger.error("Unable to schedule heart beat", e);
            }
        }
    }

    /**
     * License load failure resets the heartbeat back to the default state
     */
    @Override
    public synchronized void onLicenseFail()
    {
        boolean newEnabled = dataCollectorService.isEnabledByDefault();

        if (newEnabled != this.enabled)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("State change of heartbeat");
            }
            this.enabled = newEnabled;
            dataCollectorService.enabled(newEnabled);
            try
            {
                scheduleJob();
            }
            catch (Exception e)
            {
                logger.error("Unable to schedule heart beat", e);
            }
        }
    }

    /**
     * Start or stop the hertbeat job depending on whether the heartbeat is enabled or not
     * @throws SchedulerException
     */
    private synchronized void scheduleJob() throws SchedulerException
    {
        // Schedule the heart beat to run regularly
        final String triggerName = JOB_NAME + "Trigger";
        if(this.enabled)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("heartbeat job scheduled");
            }
            final JobDetail jobDetail = new JobDetail(JOB_NAME, Scheduler.DEFAULT_GROUP, HeartBeatJob.class);
            jobDetail.getJobDataMap().put("heartBeat", this);
            
            // Ensure the job wasn't already scheduled in an earlier retry of this transaction
            scheduler.unscheduleJob(triggerName, Scheduler.DEFAULT_GROUP);
            final Trigger trigger = new SimpleTrigger(triggerName, Scheduler.DEFAULT_GROUP, new Date(), null,
                    //SimpleTrigger.REPEAT_INDEFINITELY, testMode ? 1000 : 4 * 60 * 60 * 1000);
                    SimpleTrigger.REPEAT_INDEFINITELY, testMode ? 1000 : 2 * 60 * 1000);
            scheduler.scheduleJob(jobDetail, trigger);
        }
        else
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("heartbeat job unscheduled");
            }
            scheduler.unscheduleJob(triggerName, Scheduler.DEFAULT_GROUP);
        }
    }

    /**
     * The scheduler job responsible for triggering a heartbeat on a regular basis.
     */
    public static class HeartBeatJob implements Job
    {
        public void execute(final JobExecutionContext jobexecutioncontext) throws JobExecutionException
        {
            final JobDataMap dataMap = jobexecutioncontext.getJobDetail().getJobDataMap();
            final HeartBeat heartBeat = (HeartBeat) dataMap.get("heartBeat");
            try
            {
                heartBeat.collectAndSendData();
            }
            catch (final Exception e)
            {
                if (logger.isDebugEnabled())
                {
                    // Verbose logging
                    HeartBeat.logger.debug("Heartbeat job failure", e);
                }
                else
                {
                    // Heartbeat errors are non-fatal and will show as single line warnings
                    HeartBeat.logger.warn(e.toString());
                    throw new JobExecutionException(e);
                }
            }
        }
    }
}
