package com.ctrip.platform.dal.dao.datasource.cluster;

import com.ctrip.platform.dal.dao.helper.DalElementFactory;
import com.ctrip.platform.dal.dao.log.ILogger;
import com.ctrip.platform.dal.exceptions.DalException;
import com.ctrip.platform.dal.exceptions.InvalidConnectionException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MajorityHostRouteStrategy implements RouteStrategy{

    private static final ILogger LOGGER = DalElementFactory.DEFAULT.getILogger();
    private static final String CAT_LOG_TYPE = "DAL.pickConnection";
    private static final String HOST_NOT_EXIST = "Router::hostNotExist:%s";
    private static final String NO_HOST_AVAILABLE = "Router::noHostAvailable:%s";

    private ConnectionValidator connectionValidator;
    private HostValidator hostValidator;

    public MajorityHostRouteStrategy() {
        MajorityHostValidator validator = new MajorityHostValidator();
        connectionValidator = validator;
        hostValidator = validator;
    }

    @Override
    public Connection pickConnection(ConnectionFactory factory, RequestContext context, RouteOptions options) throws SQLException {
        for (int i = 0; i < 9; i++) {
            try {
                String clientZone = context.clientZone();

                HostSpec targetHost = pickHost(factory, options, clientZone);
                Connection targetConnection = factory.getPooledConnectionForHost(targetHost);

                return targetConnection;
            } catch (InvalidConnectionException e) {
                // TODO log something
            } catch (DalException e) {
                LOGGER.error(String.format(NO_HOST_AVAILABLE, " "), e);
                throw e;
            }
        }

        throw new DalException(NO_HOST_AVAILABLE);
    }

    @Override
    public ConnectionValidator getConnectionValidator() {
        return connectionValidator;
    }

    private HostSpec pickHost(ConnectionFactory factory, RouteOptions options, String clientZone) throws DalException {
        List<HostSpec> orderHosts = options.orderedMasters(clientZone);

        for (HostSpec hostSpec : orderHosts) {
            if (hostValidator.available(factory, hostSpec, options)) {
                return hostSpec;
            }
        }

        throw new DalException(String.format(NO_HOST_AVAILABLE, orderHosts.toString()));
    }
}
