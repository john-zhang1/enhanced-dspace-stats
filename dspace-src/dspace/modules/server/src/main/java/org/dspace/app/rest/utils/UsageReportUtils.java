/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.utils;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.dspace.app.rest.model.UsageReportPointCityRest;
import org.dspace.app.rest.model.UsageReportPointCountryRest;
import org.dspace.app.rest.model.UsageReportPointDateRest;
import org.dspace.app.rest.model.UsageReportPointDsoTotalVisitsRest;
import org.dspace.app.rest.model.UsageReportRest;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.DSpaceObjectLegacySupport;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.service.HandleService;
import org.dspace.statistics.Dataset;
import org.dspace.statistics.ObjectCount;
import org.dspace.statistics.content.DatasetDSpaceObjectGenerator;
import org.dspace.statistics.content.DatasetTimeGenerator;
import org.dspace.statistics.content.DatasetTypeGenerator;
import org.dspace.statistics.content.StatisticsDataVisits;
import org.dspace.statistics.content.StatisticsListing;
import org.dspace.statistics.content.StatisticsTable;
import org.dspace.statistics.factory.StatisticsServiceFactory;
import org.dspace.statistics.service.SolrLoggerService;
import org.dspace.statistics.util.LocationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * This is the Service dealing with the {@link UsageReportRest} logic
 *
 * @author Maria Verdonck (Atmire) on 08/06/2020
 */
@Component
public class UsageReportUtils {

    @Autowired
    private HandleService handleService;

    protected final SolrLoggerService solrLoggerService = StatisticsServiceFactory.getInstance().getSolrLoggerService();
    protected transient final ItemService itemService = ContentServiceFactory.getInstance().getItemService();

    public static final String TOTAL_VISITS_REPORT_ID = "TotalVisits";
    public static final String TOTAL_NUM_PAGEVIEWS_REPORT_ID = "TotalNumberOfPageviews";
    public static final String TOTAL_NUM_DOWNLOADS_REPORT_ID = "TotalNumberOfDownloads";
    public static final String TOTAL_VISITS_PER_MONTH_REPORT_ID = "TotalVisitsPerMonth";
    public static final String TOTAL_PAGEVIEWS_PER_MONTH_REPORT_ID = "TotalPageviewsPerMonth";
    public static final String TOTAL_DOWNLOADS_PER_MONTH_REPORT_ID = "TotalDownloadsPerMonth";
    public static final String TOTAL_DOWNLOADS_REPORT_ID = "TotalDownloads";
    public static final String TOP_COUNTRIES_REPORT_ID = "TopCountries";
    public static final String TOP_COUNTRIES_PAGEVIEWS_REPORT_ID = "TopCountriesPageviews";
    public static final String TOP_COUNTRIES_DOWNLOADS_REPORT_ID = "TopCountriesDownloads";
    public static final String TOP_CITIES_REPORT_ID = "TopCities";
    public static final String TOP_ITEMS_PAGEVIEWS_REPORT_ID = "TopItemsPageviews";
    public static final String TOP_ITEMS_DOWNLOADS_REPORT_ID = "TopItemsDownloads";
    public static final String TOP_CITIES_PAGEVIEWS_REPORT_ID = "TopCitiesPageviews";
    public static final String TOP_CITIES_DOWNLOADS_REPORT_ID = "TopCitiesDownloads";


    /**
     * Get list of usage reports that are applicable to the DSO (of given UUID)
     *
     * @param context   DSpace context
     * @param dso       DSpaceObject we want all available usage reports of
     * @return List of usage reports, applicable to the given DSO
     */
    public List<UsageReportRest> getUsageReportsOfDSO(Context context, DSpaceObject dso)
        throws SQLException, ParseException, SolrServerException, IOException {
        List<UsageReportRest> usageReports = new ArrayList<>();

        usageReports.add(this.createUsageReport(context, dso, TOTAL_NUM_PAGEVIEWS_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOTAL_NUM_DOWNLOADS_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOTAL_PAGEVIEWS_PER_MONTH_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOTAL_DOWNLOADS_PER_MONTH_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOP_COUNTRIES_PAGEVIEWS_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOP_COUNTRIES_DOWNLOADS_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOP_CITIES_PAGEVIEWS_REPORT_ID));
        usageReports.add(this.createUsageReport(context, dso, TOP_CITIES_DOWNLOADS_REPORT_ID));

        if (dso instanceof Site || dso instanceof Community || dso instanceof Collection) {
            usageReports.add(this.createUsageReport(context, dso, TOP_ITEMS_PAGEVIEWS_REPORT_ID));
            usageReports.add(this.createUsageReport(context, dso, TOP_ITEMS_DOWNLOADS_REPORT_ID));
        }
        if (dso instanceof Item || dso instanceof Bitstream) {
            usageReports.add(this.createUsageReport(context, dso, TOTAL_DOWNLOADS_REPORT_ID));
        }

        return usageReports;
    }

    /**
     * Creates the stat different stat usage report based on the report id.
     * If the report id or the object uuid is invalid, an exception is thrown.
     *
     * @param context  DSpace context
     * @param dso     DSpace object we want a stat usage report on
     * @param reportId Type of usage report requested
     * @return Rest object containing the stat usage report, see {@link UsageReportRest}
     */
    public UsageReportRest createUsageReport(Context context, DSpaceObject dso, String reportId)
        throws ParseException, SolrServerException, IOException {
        try {
            UsageReportRest usageReportRest;
            switch (reportId) {
                case TOTAL_VISITS_REPORT_ID:
                    usageReportRest = resolveTotalVisits(context, dso);
                    usageReportRest.setReportType(TOTAL_VISITS_REPORT_ID);
                    break;
                case TOTAL_NUM_PAGEVIEWS_REPORT_ID:
                    usageReportRest = resolveTotalNumberOfPageviews(context, dso);
                    usageReportRest.setReportType(TOTAL_NUM_PAGEVIEWS_REPORT_ID);
                    break;
                case TOTAL_NUM_DOWNLOADS_REPORT_ID:
                    usageReportRest = resolveTotalNumberOfDownloads(context, dso);
                    usageReportRest.setReportType(TOTAL_NUM_DOWNLOADS_REPORT_ID);
                    break;
                case TOTAL_VISITS_PER_MONTH_REPORT_ID:
                    usageReportRest = resolveTotalVisitsPerMonth(context, dso);
                    usageReportRest.setReportType(TOTAL_VISITS_PER_MONTH_REPORT_ID);
                    break;
                case TOTAL_PAGEVIEWS_PER_MONTH_REPORT_ID:
                    usageReportRest = resolveTotalPageviewsPerMonth(context, dso);
                    usageReportRest.setReportType(TOTAL_PAGEVIEWS_PER_MONTH_REPORT_ID);
                    break;
                case TOTAL_DOWNLOADS_PER_MONTH_REPORT_ID:
                    usageReportRest = resolveTotalDownloadsPerMonth(context, dso);
                    usageReportRest.setReportType(TOTAL_DOWNLOADS_PER_MONTH_REPORT_ID);
                    break;
                case TOTAL_DOWNLOADS_REPORT_ID:
                    usageReportRest = resolveTotalDownloads(context, dso);
                    usageReportRest.setReportType(TOTAL_DOWNLOADS_REPORT_ID);
                    break;
                case TOP_COUNTRIES_REPORT_ID:
                    usageReportRest = resolveTopCountries(context, dso);
                    usageReportRest.setReportType(TOP_COUNTRIES_REPORT_ID);
                    break;
                case TOP_COUNTRIES_PAGEVIEWS_REPORT_ID:
                    usageReportRest = resolveTopCountriesPageviews(context, dso);
                    usageReportRest.setReportType(TOP_COUNTRIES_PAGEVIEWS_REPORT_ID);
                    break;
                case TOP_COUNTRIES_DOWNLOADS_REPORT_ID:
                    usageReportRest = resolveTopCountriesDownloads(context, dso);
                    usageReportRest.setReportType(TOP_COUNTRIES_DOWNLOADS_REPORT_ID);
                    break;
                case TOP_CITIES_REPORT_ID:
                    usageReportRest = resolveTopCities(context, dso);
                    usageReportRest.setReportType(TOP_CITIES_REPORT_ID);
                    break;
                case TOP_ITEMS_PAGEVIEWS_REPORT_ID:
                    usageReportRest = resolveTopItemsPageviewsReport(context, dso);
                    usageReportRest.setReportType(TOP_ITEMS_PAGEVIEWS_REPORT_ID);
                    break;
                case TOP_ITEMS_DOWNLOADS_REPORT_ID:
                    usageReportRest = resolveTopItemsDownloadsReport(context, dso);
                    usageReportRest.setReportType(TOP_ITEMS_DOWNLOADS_REPORT_ID);
                    break;
                case TOP_CITIES_PAGEVIEWS_REPORT_ID:
                    usageReportRest = resolveTopCitiesPageviews(context, dso);
                    usageReportRest.setReportType(TOP_CITIES_PAGEVIEWS_REPORT_ID);
                    break;
                case TOP_CITIES_DOWNLOADS_REPORT_ID:
                    usageReportRest = resolveTopCitiesDownloads(context, dso);
                    usageReportRest.setReportType(TOP_CITIES_DOWNLOADS_REPORT_ID);
                    break;
                default:
                    throw new ResourceNotFoundException("The given report id can't be resolved: " + reportId + "; " +
                                                        "available reports: TotalVisits, TotalVisitsPerMonth, " +
                                                        "TotalDownloads, TopCountries, TopCities");
            }
            usageReportRest.setId(dso.getID().toString() + "_" +  reportId);
            return usageReportRest;
        } catch (SQLException e) {
            throw new SolrServerException("SQLException trying to receive statistics of: " + dso.getID());
        }
    }

    private UsageReportRest resolveTopItemsPageviewsReport(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {
        StatisticsListing statListing = new StatisticsListing(new StatisticsDataVisits(dso));
        // Adding a new generator for our top 10 items without a name length delimiter
        DatasetDSpaceObjectGenerator dsoAxis = new DatasetDSpaceObjectGenerator();
        dsoAxis.addDsoChild(Constants.ITEM, 10, false, -1);
        dsoAxis.setIncludeTotal(true);
        statListing.addDatasetGenerator(dsoAxis);
        Dataset dataset = statListing.getDataset(context, 1);
        UsageReportRest usageReportRest = new UsageReportRest();

        for (int i = 0; i < dataset.getColLabels().size(); i++) {
            UsageReportPointDsoTotalVisitsRest totalVisitPoint = new UsageReportPointDsoTotalVisitsRest();

            totalVisitPoint.setType("item");
            String urlOfItem = dataset.getColLabelsAttrs().get(i).get("url");

            if (urlOfItem != null) {
                String handle = StringUtils.substringAfterLast(urlOfItem, "handle/");

                if (handle != null) {
                    DSpaceObject dsoItem = handleService.resolveToObject(context, handle);
                    totalVisitPoint.setId(dsoItem != null ? dsoItem.getID().toString() : urlOfItem);
                    totalVisitPoint.setLabel(dsoItem != null ? dsoItem.getName() : urlOfItem);
                    totalVisitPoint.addValue("views", Integer.valueOf(dataset.getMatrix()[0][i]));
                    usageReportRest.addPoint(totalVisitPoint);
                }
            }
        }
        usageReportRest.setReportType(TOP_ITEMS_PAGEVIEWS_REPORT_ID);
        return usageReportRest;
    }

    private UsageReportRest resolveTopItemsDownloadsReport(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:0";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningItem:" + dso.getID() + " OR owningItem:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningItem:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view) AND -(bundleName:[* TO *] AND -bundleName:ORIGINAL)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, "owningItem", 0, 10, null, null, null, null, null, false, 1, true);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<FacetField> fieldFacets = (List<FacetField>) response.getFacetFields();

        for (FacetField fieldFacet: fieldFacets) {
            if (fieldFacet.getName().equalsIgnoreCase("owningItem")) {
                for (int i = 0; i < fieldFacet.getValues().size(); i++) {
                    UsageReportPointDsoTotalVisitsRest totalVisitPoint = new UsageReportPointDsoTotalVisitsRest();
                    totalVisitPoint.setType("item");
                    totalVisitPoint.setId(fieldFacet.getValues().get(i).getName());
                    totalVisitPoint.setLabel(getItemTitle(fieldFacet.getValues().get(i).getName(), context));
                    totalVisitPoint.addValue("views", (int) fieldFacet.getValues().get(i).getCount());
                    usageReportRest.addPoint(totalVisitPoint);
                }
            }
        }

        usageReportRest.setReportType(TOP_ITEMS_DOWNLOADS_REPORT_ID);
        return usageReportRest;
    }

    public long getTotalNumberOfPageviews(DSpaceObject dso) throws SolrServerException, IOException {
        String dsoid = dso.getID().toString();
        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view)";
        String query = "";
        int facetMinCount = 1;

        if (dso instanceof org.dspace.content.Site) {
            query = "type:2";
        } else if (dso instanceof org.dspace.content.Community) {
            query = "type:2 AND owningComm:" + dsoid;
        } else if (dso instanceof org.dspace.content.Collection) {
            query = "type:2 AND owningColl:" + dsoid;
        } else if (dso instanceof org.dspace.content.Item) {
            query = "type:2 AND id:" + dsoid;
        }

        return solrLoggerService.queryTotal(query, filterQuery, facetMinCount).getCount();
    }

    public long getTotalNumberOfDownloads(DSpaceObject dso) throws SolrServerException, IOException {
        String dsoid = dso.getID().toString();
        int facetMinCount = 1;
        String query = "";
        String filterQuery = "-isBot:true AND -(bundleName:[* TO *]-bundleName:ORIGINAL) AND -(statistics_type:[* TO *] AND -statistics_type:view)";

        if (dso instanceof org.dspace.content.Site) {
            query = "type: 0";
        } else if (dso instanceof org.dspace.content.Community) {
            query = "type: 0 AND owningComm:" + dsoid;
        } else if (dso instanceof org.dspace.content.Collection) {
            query = "type: 0 AND owningColl:" + dsoid;
        } else if (dso instanceof org.dspace.content.Item) {
            query = "type: 0 AND owningItem:" + dsoid;
        }

        return solrLoggerService.queryTotal(query, filterQuery, facetMinCount).getCount();
    }

    private UsageReportRest resolveTotalNumberOfPageviews(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        UsageReportRest usageReportRest = new UsageReportRest();
        UsageReportPointDsoTotalVisitsRest totalVisitPoint = new UsageReportPointDsoTotalVisitsRest();
        totalVisitPoint.setType("item");
        totalVisitPoint.setId(dso.getID().toString());
        totalVisitPoint.setLabel(dso.getName());
        totalVisitPoint.addValue("views", (int) getTotalNumberOfPageviews(dso));
        usageReportRest.addPoint(totalVisitPoint);
        usageReportRest.setReportType(TOTAL_NUM_PAGEVIEWS_REPORT_ID);
        return usageReportRest;
    }

    private UsageReportRest resolveTotalNumberOfDownloads(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        UsageReportRest usageReportRest = new UsageReportRest();
        UsageReportPointDsoTotalVisitsRest totalVisitPoint = new UsageReportPointDsoTotalVisitsRest();
        totalVisitPoint.setType("item");
        totalVisitPoint.setId(dso.getID().toString());
        totalVisitPoint.setLabel(dso.getName());
        totalVisitPoint.addValue("views", (int) getTotalNumberOfDownloads(dso));
        usageReportRest.addPoint(totalVisitPoint);
        usageReportRest.setReportType(TOTAL_NUM_DOWNLOADS_REPORT_ID);
        return usageReportRest;
    }

    /**
     * Create a stat usage report for the amount of TotalVisit on a DSO, containing one point with the amount of
     * views on the DSO in. If there are no views on the DSO this point contains views=0.
     *
     * @param context DSpace context
     * @param dso     DSO we want usage report with TotalVisits on the DSO
     * @return Rest object containing the TotalVisits usage report of the given DSO
     */
    private UsageReportRest resolveTotalVisits(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {
        Dataset dataset = this.getDSOStatsDataset(context, dso, 1, dso.getType());

        UsageReportRest usageReportRest = new UsageReportRest();
        UsageReportPointDsoTotalVisitsRest totalVisitPoint = new UsageReportPointDsoTotalVisitsRest();
        totalVisitPoint.setType(StringUtils.substringAfterLast(dso.getClass().getName().toLowerCase(), "."));
        totalVisitPoint.setId(dso.getID().toString());
        if (dataset.getColLabels().size() > 0) {
            totalVisitPoint.setLabel(dso.getName());
            totalVisitPoint.addValue("views", Integer.valueOf(dataset.getMatrix()[0][0]));
        } else {
            totalVisitPoint.setLabel(dso.getName());
            totalVisitPoint.addValue("views", 0);
        }

        usageReportRest.addPoint(totalVisitPoint);
        return usageReportRest;
    }

    /**
     * Create a stat usage report for the amount of TotalVisitPerMonth on a DSO, containing one point for each month
     * with the views on that DSO in that month with the range -6 months to now. If there are no views on the DSO
     * in a month, the point on that month contains views=0.
     *
     * @param context DSpace context
     * @param dso     DSO we want usage report with TotalVisitsPerMonth to the DSO
     * @return Rest object containing the TotalVisits usage report on the given DSO
     */
    private UsageReportRest resolveTotalVisitsPerMonth(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {
        StatisticsTable statisticsTable = new StatisticsTable(new StatisticsDataVisits(dso));
        DatasetTimeGenerator timeAxis = new DatasetTimeGenerator();
        // TODO month start and end as request para?
        timeAxis.setDateInterval("month", "-6", "+1");
        statisticsTable.addDatasetGenerator(timeAxis);
        DatasetDSpaceObjectGenerator dsoAxis = new DatasetDSpaceObjectGenerator();
        dsoAxis.addDsoChild(dso.getType(), 10, false, -1);
        statisticsTable.addDatasetGenerator(dsoAxis);
        Dataset dataset = statisticsTable.getDataset(context, 0);

        UsageReportRest usageReportRest = new UsageReportRest();
        for (int i = 0; i < dataset.getColLabels().size(); i++) {
            UsageReportPointDateRest monthPoint = new UsageReportPointDateRest();
            monthPoint.setId(dataset.getColLabels().get(i));
            monthPoint.addValue("views", Integer.valueOf(dataset.getMatrix()[0][i]));
            usageReportRest.addPoint(monthPoint);
        }
        return usageReportRest;
    }

    private UsageReportRest resolveTotalPageviewsPerMonth(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:2";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(id:" + dso.getID() + " OR id:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND id:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, null, 0, 10, "MONTH", "-6", "+1", null, null, false, 0, true);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<RangeFacet> rangeFacets = response.getFacetRanges();
        for (RangeFacet rangeFacet: rangeFacets) {
            if (rangeFacet.getName().equalsIgnoreCase("time")) {
                RangeFacet timeFacet = rangeFacet;
                ObjectCount[] result = new ObjectCount[timeFacet.getCounts().size() + 1];
                for (int i = 0; i < timeFacet.getCounts().size(); i++) {
                    RangeFacet.Count dateCount = (RangeFacet.Count) timeFacet.getCounts().get(i);
                    result[i] = new ObjectCount();
                    result[i].setCount(dateCount.getCount());
                    result[i].setValue(solrLoggerService.getDateView(dateCount.getValue(), "MONTH", context));

                    UsageReportPointDateRest monthPoint = new UsageReportPointDateRest();
                    monthPoint.setId(result[i].getValue());
                    monthPoint.addValue("views", (int)result[i].getCount());
                    usageReportRest.addPoint(monthPoint);
                }
                result[result.length - 1] = new ObjectCount();
                result[result.length - 1].setCount(response.getResults().getNumFound());
                result[result.length - 1].setValue("total");
            }
        }

        return usageReportRest;
    }

    private UsageReportRest resolveTotalDownloadsPerMonth(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:0";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningItem:" + dso.getID() + " OR owningItem:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningItem:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view) AND -(bundleName:[* TO *] AND -bundleName:ORIGINAL)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, null, 0, 10, "MONTH", "-6", "+1", null, null, false, 0, true);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<RangeFacet> rangeFacets = response.getFacetRanges();
        for (RangeFacet rangeFacet: rangeFacets) {
            if (rangeFacet.getName().equalsIgnoreCase("time")) {
                RangeFacet timeFacet = rangeFacet;
                // ObjectCount[] result = new ObjectCount[timeFacet.getCounts().size() + 1];
                for (int i = 0; i < timeFacet.getCounts().size(); i++) {
                    RangeFacet.Count dateCount = (RangeFacet.Count) timeFacet.getCounts().get(i);
                    UsageReportPointDateRest monthPoint = new UsageReportPointDateRest();
                    monthPoint.setId(solrLoggerService.getDateView(dateCount.getValue(), "MONTH", context));
                    monthPoint.addValue("views", dateCount.getCount());
                    usageReportRest.addPoint(monthPoint);
                }
            }
        }

        return usageReportRest;
    }

    /**
     * Create a stat usage report for the amount of TotalDownloads on the files of an Item or of a Bitstream,
     * containing a point for each bitstream of the item that has been visited at least once or one point for the
     * bitstream containing the amount of times that bitstream has been visited (even if 0)
     * If the item has no bitstreams, or no bitstreams that have ever been downloaded/visited, then it contains an
     * empty list of points=[]
     * If the given UUID is for DSO that is neither a Bitstream nor an Item, an exception is thrown.
     *
     * @param context DSpace context
     * @param dso     Item/Bitstream we want usage report on with TotalDownloads of the Item's bitstreams or of the
     *                bitstream itself
     * @return Rest object containing the TotalDownloads usage report on the given Item/Bitstream
     */
    private UsageReportRest resolveTotalDownloads(Context context, DSpaceObject dso)
        throws SQLException, SolrServerException, ParseException, IOException {
        if (dso instanceof org.dspace.content.Bitstream) {
            return this.resolveTotalVisits(context, dso);
        }

        if (dso instanceof org.dspace.content.Item) {
            Dataset dataset = this.getDSOStatsDataset(context, dso, 1, Constants.BITSTREAM);

            UsageReportRest usageReportRest = new UsageReportRest();
            for (int i = 0; i < dataset.getColLabels().size(); i++) {
                UsageReportPointDsoTotalVisitsRest totalDownloadsPoint = new UsageReportPointDsoTotalVisitsRest();
                totalDownloadsPoint.setType("bitstream");

                totalDownloadsPoint.setId(dataset.getColLabelsAttrs().get(i).get("id"));
                totalDownloadsPoint.setLabel(dataset.getColLabels().get(i));

                totalDownloadsPoint.addValue("views", Integer.valueOf(dataset.getMatrix()[0][i]));
                usageReportRest.addPoint(totalDownloadsPoint);
            }
            return usageReportRest;
        }
        throw new IllegalArgumentException("TotalDownloads report only available for items and bitstreams");
    }

    /**
     * Create a stat usage report for the TopCountries that have visited the given DSO. If there have been no visits, or
     * no visits with a valid Geolite determined country (based on IP), this report contains an empty list of points=[].
     * The list of points is limited to the top 100 countries, and each point contains the country name, its iso code
     * and the amount of views on the given DSO from that country.
     *
     * @param context DSpace context
     * @param dso     DSO we want usage report of the TopCountries on the given DSO
     * @return Rest object containing the TopCountries usage report on the given DSO
     */
    private UsageReportRest resolveTopCountries(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {
        Dataset dataset = this.getTypeStatsDataset(context, dso, "countryCode", 1);

        UsageReportRest usageReportRest = new UsageReportRest();
        for (int i = 0; i < dataset.getColLabels().size(); i++) {
            UsageReportPointCountryRest countryPoint = new UsageReportPointCountryRest();
            countryPoint.setLabel(dataset.getColLabels().get(i));
            countryPoint.addValue("views", Integer.valueOf(dataset.getMatrix()[0][i]));
            usageReportRest.addPoint(countryPoint);
        }
        return usageReportRest;
    }

    private UsageReportRest resolveTopCountriesPageviews(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:2";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(id:" + dso.getID() + " OR id:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND id:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, "countryCode", 0, -1, null, null, null, null, null, false, 1, false);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<FacetField> fieldFacets = (List<FacetField>) response.getFacetFields();

        for (FacetField fieldFacet: fieldFacets) {
            if (fieldFacet.getName().equalsIgnoreCase("countryCode")) {
                for (int i = 0; i < fieldFacet.getValues().size(); i++) {
                    UsageReportPointCountryRest countryPoint = new UsageReportPointCountryRest();
                    countryPoint.setId(fieldFacet.getValues().get(i).getName());
                    countryPoint.setLabel(LocationUtils.getCountryName(fieldFacet.getValues().get(i).getName(), context.getCurrentLocale()));
                    countryPoint.addValue("views", (int) fieldFacet.getValues().get(i).getCount());
                    usageReportRest.addPoint(countryPoint);
                }
            }
        }

        usageReportRest.setReportType(TOP_COUNTRIES_PAGEVIEWS_REPORT_ID);
        return usageReportRest;
    }

    private UsageReportRest resolveTopCountriesDownloads(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:0";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningItem:" + dso.getID() + " OR owningItem:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningItem:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view) AND -(bundleName:[* TO *] AND -bundleName:ORIGINAL)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, "countryCode", 0, -1, null, null, null, null, null, false, 1, false);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<FacetField> fieldFacets = (List<FacetField>) response.getFacetFields();

        for (FacetField fieldFacet: fieldFacets) {
            if (fieldFacet.getName().equalsIgnoreCase("countryCode")) {
                for (int i = 0; i < fieldFacet.getValues().size(); i++) {
                    UsageReportPointCountryRest countryPoint = new UsageReportPointCountryRest();
                    countryPoint.setId(fieldFacet.getValues().get(i).getName());
                    countryPoint.setLabel(LocationUtils.getCountryName(fieldFacet.getValues().get(i).getName(), context.getCurrentLocale()));
                    countryPoint.addValue("views", (int) fieldFacet.getValues().get(i).getCount());
                    usageReportRest.addPoint(countryPoint);
                }
            }
        }

        usageReportRest.setReportType(TOP_COUNTRIES_DOWNLOADS_REPORT_ID);
        return usageReportRest;
    }

    /**
     * Create a stat usage report for the TopCities that have visited the given DSO. If there have been no visits, or
     * no visits with a valid Geolite determined city (based on IP), this report contains an empty list of points=[].
     * The list of points is limited to the top 100 cities, and each point contains the city name and the amount of
     * views on the given DSO from that city.
     *
     * @param context DSpace context
     * @param dso     DSO we want usage report of the TopCities on the given DSO
     * @return Rest object containing the TopCities usage report on the given DSO
     */
    private UsageReportRest resolveTopCities(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {
        Dataset dataset = this.getTypeStatsDataset(context, dso, "city", 1);

        UsageReportRest usageReportRest = new UsageReportRest();
        for (int i = 0; i < dataset.getColLabels().size(); i++) {
            UsageReportPointCityRest cityPoint = new UsageReportPointCityRest();
            cityPoint.setId(dataset.getColLabels().get(i));
            cityPoint.addValue("views", Integer.valueOf(dataset.getMatrix()[0][i]));
            usageReportRest.addPoint(cityPoint);
        }
        return usageReportRest;
    }

    private UsageReportRest resolveTopCitiesPageviews(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:2";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(id:" + dso.getID() + " OR id:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND id:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, "city", 0, -1, null, null, null, null, null, false, 1, false);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<FacetField> fieldFacets = (List<FacetField>) response.getFacetFields();

        for (FacetField fieldFacet: fieldFacets) {
            if (fieldFacet.getName().equalsIgnoreCase("city")) {
                for (int i = 0; i < fieldFacet.getValues().size(); i++) {
                    UsageReportPointCityRest cityPoint = new UsageReportPointCityRest();
                    cityPoint.setId(fieldFacet.getValues().get(i).getName());
                    cityPoint.setLabel(LocationUtils.getCountryName(fieldFacet.getValues().get(i).getName(), context.getCurrentLocale()));
                    cityPoint.addValue("views", (int) fieldFacet.getValues().get(i).getCount());
                    usageReportRest.addPoint(cityPoint);
                }
            }
        }

        usageReportRest.setReportType(TOP_CITIES_PAGEVIEWS_REPORT_ID);
        return usageReportRest;
    }

    private UsageReportRest resolveTopCitiesDownloads(Context context, DSpaceObject dso)
        throws SQLException, IOException, ParseException, SolrServerException {

        String query = "type:0";
        if (dso instanceof org.dspace.content.Site) {
            query += "";
        } else if (dso instanceof org.dspace.content.Community) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningComm:" + dso.getID() + " OR owningComm:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningComm:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Collection) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningColl:" + dso.getID() + " OR owningColl:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningColl:" + dso.getID();
            }
        } else if (dso instanceof org.dspace.content.Item) {
            if (dso instanceof DSpaceObjectLegacySupport) {
                query += " AND +(owningItem:" + dso.getID() + " OR owningItem:" + ((DSpaceObjectLegacySupport) dso).getLegacyId() + ")";
            } else {
                query += " AND owningItem:" + dso.getID();
            }
        }

        String filterQuery = "-isBot:true AND -(statistics_type:[* TO *] AND -statistics_type:view) AND -(bundleName:[* TO *] AND -bundleName:ORIGINAL)";
        QueryResponse response = solrLoggerService.query(query, filterQuery, "city", 0, -1, null, null, null, null, null, false, 1, false);
        UsageReportRest usageReportRest = new UsageReportRest();
        List<FacetField> fieldFacets = (List<FacetField>) response.getFacetFields();

        for (FacetField fieldFacet: fieldFacets) {
            if (fieldFacet.getName().equalsIgnoreCase("city")) {
                for (int i = 0; i < fieldFacet.getValues().size(); i++) {
                    UsageReportPointCityRest cityPoint = new UsageReportPointCityRest();
                    cityPoint.setId(fieldFacet.getValues().get(i).getName());
                    cityPoint.setLabel(LocationUtils.getCountryName(fieldFacet.getValues().get(i).getName(), context.getCurrentLocale()));
                    cityPoint.addValue("views", (int) fieldFacet.getValues().get(i).getCount());
                    usageReportRest.addPoint(cityPoint);
                }
            }
        }

        usageReportRest.setReportType(TOP_CITIES_DOWNLOADS_REPORT_ID);
        return usageReportRest;
    }

    /**
     * Retrieves the stats dataset of a given DSO, of given type, with a given facetMinCount limit (usually either 0
     * or 1, 0 if we want a data point even though the facet data point has 0 matching results).
     *
     * @param context       DSpace context
     * @param dso           DSO we want the stats dataset of
     * @param facetMinCount Minimum amount of results on a facet data point for it to be added to dataset
     * @param dsoType       Type of DSO we want the stats dataset of
     * @return Stats dataset with the given filters.
     */
    private Dataset getDSOStatsDataset(Context context, DSpaceObject dso, int facetMinCount, int dsoType)
        throws SQLException, IOException, ParseException, SolrServerException {
        StatisticsListing statsList = new StatisticsListing(new StatisticsDataVisits(dso));
        DatasetDSpaceObjectGenerator dsoAxis = new DatasetDSpaceObjectGenerator();
        dsoAxis.addDsoChild(dsoType, 10, true, -1);
        statsList.addDatasetGenerator(dsoAxis);
        return statsList.getDataset(context, facetMinCount);
    }

    /**
     * Retrieves the stats dataset of a given dso, with a given axisType (example countryCode, city), which
     * corresponds to a solr field, and a given facetMinCount limit (usually either 0 or 1, 0 if we want a data point
     * even though the facet data point has 0 matching results).
     *
     * @param context        DSpace context
     * @param dso            DSO we want the stats dataset of
     * @param typeAxisString String of the type we want on the axis of the dataset (corresponds to solr field),
     *                       examples: countryCode, city
     * @param facetMinCount  Minimum amount of results on a facet data point for it to be added to dataset
     * @return Stats dataset with the given type on the axis, of the given DSO and with given facetMinCount
     */
    private Dataset getTypeStatsDataset(Context context, DSpaceObject dso, String typeAxisString, int facetMinCount)
        throws SQLException, IOException, ParseException, SolrServerException {
        StatisticsListing statListing = new StatisticsListing(new StatisticsDataVisits(dso));
        DatasetTypeGenerator typeAxis = new DatasetTypeGenerator();
        typeAxis.setType(typeAxisString);
        // TODO make max nr of top countries/cities a request para? Must be set
        typeAxis.setMax(100);
        statListing.addDatasetGenerator(typeAxis);
        return statListing.getDataset(context, facetMinCount);
    }

    private String getItemTitle(String id, Context context) throws SQLException {
        String itemTitle = null;
        String dsoId = null;
        try {
            dsoId = UUID.fromString(id).toString();
        } catch (Exception e) {
            try {
                dsoId = String.valueOf(Integer.parseInt(id));
            } catch (NumberFormatException e1) {
                dsoId = null;
            }
        }
        Item item = itemService.findByIdOrLegacyId(context, dsoId);
        if (item != null) {
            String title = itemService.getMetadataFirstValue(item, "dc", "title", null, Item.ANY);
            if (title != null) {
                itemTitle = title;
            }
        }

        return itemTitle;
    }
}
