package com.xpert.faces.primefaces;

import com.xpert.faces.bean.Xpert;
import com.xpert.faces.component.restorablefilter.RestorableFilter;
import com.xpert.i18n.XpertResourceBundle;
import com.xpert.persistence.dao.BaseDAO;
import com.xpert.persistence.query.JoinBuilder;
import com.xpert.persistence.query.QueryBuilder;
import com.xpert.persistence.query.QueryParameter;
import com.xpert.persistence.query.Restriction;
import com.xpert.persistence.query.RestrictionType;
import com.xpert.persistence.query.Restrictions;
import com.xpert.persistence.utils.EntityUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortOrder;

/**
 * A JPA based implementation os primefaces LazyDataModel
 *
 * @author ayslan
 * @param <T>
 */
public class LazyDataModelImpl<T> extends LazyDataModel {

    private boolean debug = false;
    private static final Logger logger = Logger.getLogger(LazyDataModelImpl.class.getName());

    private BaseDAO<T> dao;
    private String defaultOrder;
    private String currentOrderBy;
    private String attributes;
    private OrderByHandler orderByHandler;
    private FilterByHandler filterByHandler;
    private LazyCountType lazyCountType;
    private Integer currentRowCount;
    /*
     * to add restrictions on query to filter table
     */
    private List<Restriction> restrictions;
    private List<Restriction> queryRestrictions;
    private Restriction restriction;
    private JoinBuilder joinBuilder;
    private boolean loadData = true;
    private boolean restorableFilter = false;
    private Map currentFilters;
    private List<QueryParameter> parameters = new ArrayList<>();

    /**
     * @param attributes Attributes of object thet will be loaded
     * @param defaultOrder The default data model "Order By"
     * @param restriction A restriction to be added in query
     * @param dao An instance of BaseDAO
     */
    public LazyDataModelImpl(String attributes, String defaultOrder, Restriction restriction, BaseDAO<T> dao) {
        this.dao = dao;
        this.attributes = attributes;
        this.defaultOrder = defaultOrder;
        this.restriction = restriction;
    }

    /**
     * @param attributes Attributes of object thet will be loaded
     * @param defaultOrder The default data model "Order By"
     * @param restrictions Restrictions to be added in query
     * @param dao An instance of BaseDAO
     */
    public LazyDataModelImpl(String attributes, String defaultOrder, List<Restriction> restrictions, BaseDAO<T> dao) {
        this.dao = dao;
        this.attributes = attributes;
        this.defaultOrder = defaultOrder;
        this.restrictions = restrictions;
    }

    /**
     * @param defaultOrder The default data model "Order By"
     * @param restriction Restrictions to be added in query
     * @param dao An instance of BaseDAO
     */
    public LazyDataModelImpl(String defaultOrder, Restriction restriction, BaseDAO<T> dao) {
        this.dao = dao;
        this.defaultOrder = defaultOrder;
        this.restriction = restriction;
    }

    /**
     *
     * @param defaultOrder The default data model "Order By"
     * @param restrictions Restrictions to be added in query
     * @param dao An instance of BaseDAO
     */
    public LazyDataModelImpl(String defaultOrder, List<Restriction> restrictions, BaseDAO<T> dao) {
        this.dao = dao;
        this.defaultOrder = defaultOrder;
        this.restrictions = restrictions;
    }

    /**
     * @param defaultOrder The default data model "Order By"
     * @param dao An instance of BaseDAO
     */
    public LazyDataModelImpl(String defaultOrder, BaseDAO<T> dao) {
        this.dao = dao;
        this.defaultOrder = defaultOrder;
    }

    /**
     *
     * @param defaultOrder The default data model "Order By"
     * @param restriction A restriction to be added in query
     * @param dao An instance of BaseDAO
     * @param joinBuilder Joins to be added in Query
     */
    public LazyDataModelImpl(String defaultOrder, Restriction restriction, BaseDAO<T> dao, JoinBuilder joinBuilder) {
        this.dao = dao;
        this.defaultOrder = defaultOrder;
        this.restriction = restriction;
        this.joinBuilder = joinBuilder;
    }

    /**
     *
     * @param defaultOrder The default data model "Order By"
     * @param restrictions Restrictions to be added in query
     * @param dao An instance of BaseDAO
     * @param joinBuilder Joins to be added in Query
     */
    public LazyDataModelImpl(String defaultOrder, List<Restriction> restrictions, BaseDAO<T> dao, JoinBuilder joinBuilder) {
        this.dao = dao;
        this.defaultOrder = defaultOrder;
        this.restrictions = restrictions;
        this.joinBuilder = joinBuilder;
    }

    /**
     *
     * @param defaultOrder The default data model "Order By"
     * @param dao An instance of BaseDAO
     * @param joinBuilder Joins to be added in Query
     */
    public LazyDataModelImpl(String defaultOrder, BaseDAO<T> dao, JoinBuilder joinBuilder) {
        this.dao = dao;
        this.defaultOrder = defaultOrder;
        this.joinBuilder = joinBuilder;
    }

    /**
     * @param orderBy String from primefaces
     * @param order Order from primefaces
     * @return The "Order By" to the data model
     */
    public String getOrderBy(String orderBy, SortOrder order) {
        if (orderBy == null || orderBy.trim().isEmpty()) {
            orderBy = defaultOrder;
        } else {
            OrderByHandler orderByHandler = getOrderByHandler();
            String orderByFromHandler = null;
            if (orderByHandler != null) {
                orderByFromHandler = orderByHandler.getOrderBy(orderBy);
            }
            if (orderByHandler != null && orderByFromHandler != null && !orderByFromHandler.isEmpty()) {
                orderBy = orderByFromHandler;
            } else {
                if (joinBuilder != null && joinBuilder.getRootAlias() != null) {
                    orderBy = joinBuilder.getRootAlias() + "." + orderBy;
                }
            }
            if (order.equals(SortOrder.DESCENDING)) {
                orderBy = orderBy + " DESC";
            }
        }

        return orderBy;
    }

    /**
     * @param filters Filters from "load" method
     * @return The filter map converted into "restrictions"
     */
    public List<Restriction> getRestrictionsFromFilterMap(Map filters) {

        List<Restriction> filterRestrictions = new ArrayList<>();

        if (filters != null && !filters.isEmpty()) {
            for (Entry e : ((Map<String, Object>) filters).entrySet()) {
                if (e.getValue() != null && !e.getValue().toString().isEmpty()) {
                    FilterByHandler filterByHandler = getFilterByHandler();
                    Restrictions restrictionsFromFilterByHandler = null;
                    if (filterByHandler != null) {
                        restrictionsFromFilterByHandler = filterByHandler.getFilterBy(e.getKey().toString(), e.getValue());
                    }
                    if (filterByHandler != null && restrictionsFromFilterByHandler != null && !restrictionsFromFilterByHandler.isEmpty()) {
                        filterRestrictions.addAll(restrictionsFromFilterByHandler);
                    } else {
                        if (debug) {
                            logger.log(Level.INFO, "Restriction added. Name: {0}, Value:  {1}", new Object[]{e.getKey(), e.getValue()});
                        }
                        //primefaces 5 can add custom types in filter not only String
                        String property = e.getKey().toString();
                        if (joinBuilder != null && joinBuilder.getRootAlias() != null) {
                            property = joinBuilder.getRootAlias() + "." + property;
                        }
                        if (e.getValue() instanceof String) {
                            filterRestrictions.add(new Restriction(property, RestrictionType.DATA_TABLE_FILTER, e.getValue()));
                        } else if (e.getValue() instanceof Object[]) {
                            if (((Object[]) e.getValue()).length > 0) {
                                //copy Array into List. (Arrays.asList din't work here)
                                List list = new ArrayList();
                                for (Object item : (Object[]) e.getValue()) {
                                    list.add(item);
                                }
                                filterRestrictions.add(new Restriction(property, RestrictionType.IN, list));
                            }
                        } else {
                            filterRestrictions.add(new Restriction(property, RestrictionType.EQUALS, e.getValue()));

                        }
                    }
                }
            }
        }
        return filterRestrictions;
    }

    @Override
    public List load(int first, int pageSize, String orderBy, SortOrder order, Map filters
    ) {
        if (isLoadData() == false) {
            setRowCount(0);
            return null;
        }
        if (isRestorableFilter()) {
            RestorableFilter.restoreFilterFromSession(filters);
        }

        this.currentFilters = filters;
        long begin = System.currentTimeMillis();

        LazyCountType lazyCountType = getLazyCountType();
        if (lazyCountType == null) {
            lazyCountType = LazyCountType.ALWAYS;
        }

        orderBy = getOrderBy(orderBy, order);

        if (debug) {
            logger.log(Level.INFO, "Lazy Count Type: {0}. Using order by {1}", new Object[]{lazyCountType, orderBy});
        }

        List<Restriction> currentQueryRestrictions = getCurrentQueryRestrictions();

        this.currentOrderBy = orderBy;

        String select = null;
        if (attributes != null && !attributes.isEmpty()) {
            select = attributes;
        } else if (joinBuilder != null && joinBuilder.getRootAlias() != null && !joinBuilder.getRootAlias().isEmpty()) {
            select = joinBuilder.getRootAlias();
        }

        boolean restrictionsChanged = !currentQueryRestrictions.equals(queryRestrictions);
        //update current restrictions
        queryRestrictions = currentQueryRestrictions;

        //added distinct verification
        QueryBuilder queryBuilder = buildQueryBuilder();
        if (joinBuilder != null && joinBuilder.isDistinct()) {
            queryBuilder.selectDistinct(select);
        } else {
            queryBuilder.select(select);
        }

        List<T> dados = queryBuilder
                .addParameters(parameters)
                .orderBy(orderBy)
                .setFirstResult(first)
                .setMaxResults(pageSize)
                .getResultList();

        if (debug) {
            logger.log(Level.INFO, "Select on entity {0}, records found: {1} ", new Object[]{dao.getEntityClass().getName(), dados.size()});
        }

        //If ALWAYS or (ONLY_ONCE and not set currentRowCount or restrictions has changed)
        if (lazyCountType.equals(LazyCountType.ALWAYS)
                || (lazyCountType.equals(LazyCountType.ONLY_ONCE) && (currentRowCount == null || restrictionsChanged))) {

            QueryBuilder queryBuilderCount = buildQueryBuilder();
            //added distinct verification
            if (joinBuilder != null && joinBuilder.isDistinct()) {
                currentRowCount = queryBuilderCount.countDistinct(joinBuilder.getRootAlias()).intValue();
            } else {
                currentRowCount = queryBuilderCount.count().intValue();
            }
            if (debug) {
                logger.log(Level.INFO, "Count on entity {0}, records found: {1} ", new Object[]{dao.getEntityClass().getName(), currentRowCount});
            }
            this.setRowCount(currentRowCount);
        }
        if (lazyCountType.equals(LazyCountType.ONLY_ONCE)) {
            this.setRowCount(currentRowCount);
        } else if (lazyCountType.equals(LazyCountType.NONE)) {
            currentRowCount = dados.size();
            this.setRowCount(Integer.MAX_VALUE);
        }

        if (debug) {
            long end = System.currentTimeMillis();
            logger.log(Level.INFO, "Load method executed in {0} milliseconds", (end - begin));
        }
        if (isRestorableFilter()) {
            RestorableFilter.storeFilterInSession(filters);
        }

        return dados;
    }

    public List<Restriction> getCurrentQueryRestrictions() {
        List<Restriction> currentQueryRestrictions = new ArrayList<>();

        if (restrictions != null && !restrictions.isEmpty()) {
            currentQueryRestrictions.addAll(restrictions);
        }
        if (restriction != null) {
            currentQueryRestrictions.add(restriction);
        }
        //restrictions from filter
        if (currentFilters != null && !currentFilters.isEmpty()) {
            currentQueryRestrictions.addAll(getRestrictionsFromFilterMap(currentFilters));
        }
        return currentQueryRestrictions;
    }

    @Override
    public Object getRowData(String rowKey) {
        if (rowKey != null && !rowKey.isEmpty()) {
            //convert id (id can be integer, long, string, etc...)
            Object id = EntityUtils.getIdFromString(rowKey, getDao().getEntityClass());
            if (id != null) {
                return getDao().find(id);
            }
        }
        return null;
    }

    @Override
    public Object getRowKey(Object object) {
        if (object != null) {
            //return id from entity
            return EntityUtils.getId(object);
        }
        return null;
    }

    /**
     * return the query builder of current restrictions and join builder
     *
     * @return
     */
    public QueryBuilder buildQueryBuilder() {
        return dao.getQueryBuilder()
                .from(dao.getEntityClass(), (joinBuilder != null ? joinBuilder.getRootAlias() : null))
                .join(joinBuilder)
                .add(queryRestrictions)
                .debug(debug);
    }

    /**
     * Return a sum of field, this method gets the QueryBuilder and add the
     * current restrictions to generate the query
     *
     * @param field
     * @return
     */
    public Object sum(String field) {
        return buildQueryBuilder().sum(field);
    }

    /**
     * Return a average of field, this method gets the QueryBuilder and add the
     * current restrictions to generate the query
     *
     * @param field
     * @return
     */
    public Object avg(String field) {
        return buildQueryBuilder().avg(field);
    }

    /**
     * Return a min value of field, this method gets the QueryBuilder and add
     * the current restrictions to generate the query
     *
     * @param field
     * @return
     */
    public Object min(String field) {
        return buildQueryBuilder().min(field);
    }

    /**
     * Return a max value of field, this method gets the QueryBuilder and add
     * the current restrictions to generate the query
     *
     * @param field
     * @return
     */
    public Object max(String field) {
        return buildQueryBuilder().max(field);
    }

    /**
     * Return Paginator Template
     *
     * @return
     */
    public String getPaginatorTemplate() {
        if (isLazyCountTypeNone()) {
            return Xpert.UNKNOW_COUNT_PAGINATOR_TEMPLATE;
        }
        return Xpert.DEFAULT_PAGINATOR_TEMPLATE;
    }

    public boolean isLazyCountTypeNone() {
        LazyCountType lazyCountType = getLazyCountType();
        if (lazyCountType != null && lazyCountType.equals(LazyCountType.NONE)) {
            return true;
        }
        return false;
    }

    /**
     *
     * @return Current Page Report Template
     */
    public String getCurrentPageReportTemplate() {
        if (isLazyCountTypeNone()) {
            return new Xpert().getUnknowCountCurrentPageReportTemplate();
        }
        return new Xpert().getDefaultCurrentPageReportTemplate();
    }

    /**
     * Return the count objects from data base, based on filters from data table
     *
     * @return
     */
    public Long getCountAllResults() {

        //create a querybuilder for count
        QueryBuilder queryBuilderCount = dao.getQueryBuilder()
                .from(dao.getEntityClass(), (joinBuilder != null ? joinBuilder.getRootAlias() : null))
                .join(joinBuilder)
                .add(getCurrentQueryRestrictions())
                .debug(debug);

        Long rowCount;
        //added distinct verification
        if (joinBuilder != null && joinBuilder.isDistinct()) {
            rowCount = queryBuilderCount.countDistinct(joinBuilder.getRootAlias());
        } else {
            rowCount = queryBuilderCount.count();
        }

        return rowCount;
    }

    /**
     * Return all objects from data base, based on filters from data table
     *
     * @param orderBy
     * @return
     */
    public List getAllResults(String orderBy) {
        return dao.getQueryBuilder()
                .from(dao.getEntityClass(), (joinBuilder != null ? joinBuilder.getRootAlias() : null))
                .select(attributes)
                .add(getCurrentQueryRestrictions())
                .addParameters(parameters)
                .join(joinBuilder)
                .orderBy(orderBy)
                .debug(debug)
                .getResultList();
    }

    /**
     * @return all objects from data base, based on filters from data table
     */
    public List getAllResults() {
        return getAllResults(currentOrderBy);
    }

    @Override
    public void setRowIndex(int rowIndex) {
        if (getPageSize() == 0) {
            setPageSize(1);
        }
        super.setRowIndex(rowIndex);
    }

    public void addParameter(QueryParameter parameter) {
        if (parameter != null) {
            this.parameters.add(parameter);
        }
    }

    public void addParameters(List<QueryParameter> parameters) {
        if (parameters != null) {
            this.parameters.addAll(parameters);
        }
    }

    public List<QueryParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<QueryParameter> parameters) {
        this.parameters = parameters;
    }

    public BaseDAO<T> getDao() {
        return dao;
    }

    public void setDao(BaseDAO<T> dao) {
        this.dao = dao;
    }

    /**
     * Indicates if data will be loaded
     *
     * @return
     */
    public boolean isLoadData() {
        return loadData;
    }

    public void setLoadData(boolean loadData) {
        this.loadData = loadData;
    }

    /**
     * Default order by of query
     *
     * @return
     */
    public String getDefaultOrder() {
        return defaultOrder;
    }

    public void setDefaultOrder(String defaultOrder) {
        this.defaultOrder = defaultOrder;
    }

    /**
     * Restrictions to be added in Query
     *
     * @return
     */
    public List<Restriction> getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(List<Restriction> restrictions) {
        this.restrictions = restrictions;
    }

    public OrderByHandler getOrderByHandler() {
        return orderByHandler;
    }

    public void setOrderByHandler(OrderByHandler orderByHandler) {
        this.orderByHandler = orderByHandler;
    }

    public String getCurrentOrderBy() {
        return currentOrderBy;
    }

    public void setCurrentOrderBy(String currentOrderBy) {
        this.currentOrderBy = currentOrderBy;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    /**
     *
     * @return Current Restrictions (Data Table restrictions and defined
     * Restrictions)
     */
    public List<Restriction> getQueryRestrictions() {
        return queryRestrictions;
    }

    public void setQueryRestrictions(List<Restriction> queryRestrictions) {
        this.queryRestrictions = queryRestrictions;
    }

    public JoinBuilder getJoinBuilder() {
        return joinBuilder;
    }

    public void setJoinBuilder(JoinBuilder joinBuilder) {
        this.joinBuilder = joinBuilder;
    }

    public LazyCountType getLazyCountType() {
        return lazyCountType;
    }

    public void setLazyCountType(LazyCountType lazyCountType) {
        this.lazyCountType = lazyCountType;
    }

    public Integer getCurrentRowCount() {
        return currentRowCount;
    }

    public void setCurrentRowCount(Integer currentRowCount) {
        this.currentRowCount = currentRowCount;
    }

    public FilterByHandler getFilterByHandler() {
        return filterByHandler;
    }

    public void setFilterByHandler(FilterByHandler filterByHandler) {
        this.filterByHandler = filterByHandler;
    }

    public boolean isRestorableFilter() {
        return restorableFilter;
    }

    public void setRestorableFilter(boolean restorableFilter) {
        this.restorableFilter = restorableFilter;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

}
