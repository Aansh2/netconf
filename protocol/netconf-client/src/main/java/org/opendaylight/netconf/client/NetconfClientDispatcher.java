/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import io.netty.util.concurrent.Future;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;

public interface NetconfClientDispatcher {
    /**
     * Create netconf client. Network communication has to be set up based on network protocol specified in
     * clientConfiguration
     *
     * @param clientConfiguration Configuration of client
     * @return netconf client based on provided configuration
     */
    Future<NetconfClientSession> createClient(NetconfClientConfiguration clientConfiguration);
}
