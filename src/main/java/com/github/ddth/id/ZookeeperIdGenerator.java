package com.github.ddth.id;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.atomic.PromotedToLock;
import org.apache.curator.retry.RetryNTimes;

/**
 * This id generator utilizes Zookeeper (http://zookeeper.apache.org/) to
 * generate serial IDs.
 * 
 * @author Thanh Nguyen <btnguyen2k@gmail.com>
 * @since 0.1.0
 */
public class ZookeeperIdGenerator extends SerialIdGenerator {

    /**
     * Helper method to obtain {@link ZookeeperIdGenerator}.
     * 
     * @param zkConnString
     * @return
     */
    public static ZookeeperIdGenerator getInstance(final String zkConnString) {
        try {
            ZookeeperIdGenerator idGen = (ZookeeperIdGenerator) idGenerators.get(zkConnString,
                    new Callable<SerialIdGenerator>() {
                        @Override
                        public SerialIdGenerator call() throws Exception {
                            ZookeeperIdGenerator idGen = new ZookeeperIdGenerator();
                            idGen.setZookeeperConnString(zkConnString).init();
                            return idGen;
                        }
                    });
            return idGen;
        } catch (ExecutionException e) {
            return null;
        }
    }

    private CuratorFramework curatorFramework;
    private String zkConnString = "localhost:2181";

    public String getZookeeperConnString() {
        return zkConnString;
    }

    public ZookeeperIdGenerator setZookeeperConnString(String zkConnString) {
        this.zkConnString = zkConnString;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ZookeeperIdGenerator init() {
        super.init();

        RetryPolicy retryPolicy = new RetryNTimes(1, 2000);
        curatorFramework = CuratorFrameworkFactory.newClient(zkConnString, 3600000, 3000,
                retryPolicy);
        curatorFramework.start();

        return this;
    }

    public void destroy() {
        try {
            if (curatorFramework != null) {
                curatorFramework.close();
            }
        } catch (Exception e) {
        } finally {
            curatorFramework = null;
        }
        super.destroy();
    }

    /**
     * Calculates path for ID and LOCK from a namespace.
     * 
     * @param namespace
     * @return
     * @since 0.2.0
     */
    private static String[] calcPathIdAndPathLock(final String namespace) {
        String pathId = "/" + namespace.replaceAll("^\\/+", "").replaceAll("\\/+$", "");
        String pathLock = pathId + "/lock";
        return new String[] { pathId, pathLock };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nextId(String namespace) {
        final String[] paths = calcPathIdAndPathLock(namespace);
        final String pathId = paths[0];
        final String pathLock = paths[1];

        RetryPolicy retryPolicy = new RetryNTimes(1, 2000);
        PromotedToLock promotedToLock = PromotedToLock.builder().retryPolicy(retryPolicy)
                .lockPath(pathLock).build();
        DistributedAtomicLong dal = new DistributedAtomicLong(curatorFramework, pathId,
                retryPolicy, promotedToLock);
        try {
            AtomicValue<Long> value = dal.increment();
            if (value != null && value.succeeded()) {
                return value.postValue();
            }
            return -1;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long currentId(String namespace) {
        final String[] paths = calcPathIdAndPathLock(namespace);
        final String pathId = paths[0];
        final String pathLock = paths[1];

        RetryPolicy retryPolicy = new RetryNTimes(3, 1000);
        PromotedToLock promotedToLock = PromotedToLock.builder().retryPolicy(retryPolicy)
                .lockPath(pathLock).build();
        DistributedAtomicLong dal = new DistributedAtomicLong(curatorFramework, pathId,
                retryPolicy, promotedToLock);
        try {
            AtomicValue<Long> value = dal.get();
            if (value != null && value.succeeded()) {
                return value.postValue();
            }
            throw new RuntimeException("Operation was not successful!");
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
        }
    }
}