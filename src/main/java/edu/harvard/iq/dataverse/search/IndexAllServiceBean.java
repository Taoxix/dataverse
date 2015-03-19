package edu.harvard.iq.dataverse.search;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.IndexServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Named;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

@Named
@Stateless
public class IndexAllServiceBean {

    private static final Logger logger = Logger.getLogger(IndexAllServiceBean.class.getCanonicalName());

    @EJB
    IndexServiceBean indexService;
    @EJB
    DataverseServiceBean dataverseService;
    @EJB
    DatasetServiceBean datasetService;
    @EJB
    SystemConfig systemConfig;

    /**
     * Please note that while the indexAll method itself is async, the parameter
     * documented here effectively does nothing for 4.0. The methods that are
     * called (indexDataverse and indexDataset) are never run async for 4.0 per
     * https://github.com/IQSS/dataverse/issues/702 . We'll leave the switching
     * code in place, however so we can play more with async in the future.
     *
     * @param async To get async or non-async behavior we either call directly
     * into a method across the EJB boundary that has the @Asynchronous
     * annotation (for async) or we call into a method that doesn't have the
     * @Asynchronous annotation (for non-async).
     */
    @Asynchronous
    public Future<String> indexAll(boolean async) {
        long indexAllTimeBegin = System.currentTimeMillis();
        String status;
        SolrServer server = new HttpSolrServer("http://" + systemConfig.getSolrHostColonPort() + "/solr");
        logger.info("attempting to delete all Solr documents before a complete re-index");
        try {
            server.deleteByQuery("*:*");// CAUTION: deletes everything!
        } catch (SolrServerException | IOException ex) {
            status = ex.toString();
            logger.info(status);
            return new AsyncResult<>(status);
        }
        try {
            server.commit();
        } catch (SolrServerException | IOException ex) {
            status = ex.toString();
            logger.info(status);
            return new AsyncResult<>(status);
        }

        List<Dataverse> dataverses = dataverseService.findAll();
        int dataverseIndexCount = 0;
        for (Dataverse dataverse : dataverses) {
            dataverseIndexCount++;
            logger.info("indexing dataverse " + dataverseIndexCount + " of " + dataverses.size());
            if (async) {
                Future<String> result = indexService.indexDataverse(dataverse);
            } else {
                Future<String> result = indexService.indexDataverseNonAsync(dataverse);
            }
        }

        int datasetIndexCount = 0;
        List<Dataset> datasets = datasetService.findAll();
        for (Dataset dataset : datasets) {
            datasetIndexCount++;
            logger.info("indexing dataset " + datasetIndexCount + " of " + datasets.size());
            if (async) {
                Future<String> result = indexService.indexDataset(dataset);
            } else {
                Future<String> result = indexService.indexDatasetNonAsync(dataset);
            }
        }
//        logger.info("advanced search fields: " + advancedSearchFields);
//        logger.info("not advanced search fields: " + notAdvancedSearchFields);
        logger.info("done iterating through all datasets");

        long indexAllTimeEnd = System.currentTimeMillis();
        String timeElapsed = "index all took " + (indexAllTimeEnd - indexAllTimeBegin) + " milliseconds";
        logger.info(timeElapsed);
        status = dataverseIndexCount + " dataverses and " + datasetIndexCount + " datasets indexed " + timeElapsed + "\n";
        return new AsyncResult<>(status);
    }

}
