package com.github.ddth.id;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.ddth.id.utils.IdException;
import com.github.ddth.redis.IRedisClient;
import com.github.ddth.redis.PoolConfig;
import com.github.ddth.redis.RedisClientFactory;

/**
 * This id generator utilizes Redis (http://redis.io/) to generate serial IDs.
 * 
 * @author Thanh Nguyen <btnguyen2k@gmail.com>
 * @since 0.1.0
 */
public class RedisIdGenerator extends SerialIdGenerator {

    private static Logger LOGGER = LoggerFactory.getLogger(RedisIdGenerator.class);

    /**
     * Helper method to obtain {@link RedisIdGenerator}.
     * 
     * @param redisHost
     * @param redisPort
     * @return
     */
    public static RedisIdGenerator getInstance(String redisHost, int redisPort) {
        return getInstance(redisHost, redisPort, null, null, null);
    }

    /**
     * Helper method to obtain {@link RedisIdGenerator}.
     * 
     * @param redisHost
     * @param redisPort
     * @param redisUser
     * @param redisPassword
     * @return
     */
    public static RedisIdGenerator getInstance(String redisHost, int redisPort, String redisUser,
            String redisPassword) {
        return getInstance(redisHost, redisPort, redisUser, redisUser, null);
    }

    /**
     * Helper method to obtain {@link RedisIdGenerator}.
     * 
     * @param redisHost
     * @param redisPort
     * @param redisPoolConfig
     * @return
     */
    public static RedisIdGenerator getInstance(String redisHost, int redisPort,
            PoolConfig redisPoolConfig) {
        return getInstance(redisHost, redisPort, null, null, redisPoolConfig);
    }

    /**
     * Helper method to obtain {@link RedisIdGenerator}.
     * 
     * @param redisHost
     * @param redisPort
     * @param redisUser
     * @param redisPassword
     * @param redisPoolConfig
     * @return
     */
    public static RedisIdGenerator getInstance(final String redisHost, final int redisPort,
            final String redisUser, final String redisPassword, final PoolConfig redisPoolConfig) {
        StringBuilder key = new StringBuilder();
        key.append(redisHost).append(":").append(redisPort).append(":").append(redisUser)
                .append(":").append(redisPassword);
        try {
            RedisIdGenerator idGen = (RedisIdGenerator) idGenerators.get(key.toString(),
                    new Callable<SerialIdGenerator>() {
                        @Override
                        public SerialIdGenerator call() throws Exception {
                            RedisIdGenerator idGen = new RedisIdGenerator();
                            idGen.setRedisHost(redisHost).setRedisPort(redisPort)
                                    .setRedisUser(redisUser).setRedisPassword(redisPassword)
                                    .setRedisPoolConfig(redisPoolConfig).init();
                            return idGen;
                        }
                    });
            return idGen;
        } catch (ExecutionException e) {
            LOGGER.warn(e.getMessage(), e);
            return null;
        }
    }

    private RedisClientFactory redisFactory;
    private String redisHost = "localhost";
    private int redisPort = 6379;
    private String redisUser, redisPassword;
    private PoolConfig redisPoolConfig;

    public String getRedisHost() {
        return redisHost;
    }

    public RedisIdGenerator setRedisHost(String redisHost) {
        this.redisHost = redisHost;
        return this;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public RedisIdGenerator setRedisPort(int redisPort) {
        this.redisPort = redisPort;
        return this;
    }

    public String getRedisUser() {
        return redisUser;
    }

    public RedisIdGenerator setRedisUser(String redisUser) {
        this.redisUser = redisUser;
        return this;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public RedisIdGenerator setRedisPassword(String redisPassword) {
        this.redisPassword = redisPassword;
        return this;
    }

    public PoolConfig getRedisPoolConfig() {
        return redisPoolConfig;
    }

    public RedisIdGenerator setRedisPoolConfig(PoolConfig redisPoolConfig) {
        this.redisPoolConfig = redisPoolConfig;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RedisIdGenerator init() {
        super.init();

        redisFactory = RedisClientFactory.newFactory();
        return this;
    }

    public void destroy() {
        try {
            if (redisFactory != null) {
                redisFactory.destroy();
                redisFactory = null;
            }
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
        super.destroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nextId(final String namespace) {
        IRedisClient redisClient = redisFactory.getRedisClient(redisHost, redisPort, redisUser,
                redisPassword, redisPoolConfig);
        try {
            return redisClient.incBy(namespace, 1);
        } catch (Exception e) {
            throw new IdException.OperationFailedException(e);
        } finally {
            redisClient.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long currentId(final String namespace) {
        IRedisClient redisClient = redisFactory.getRedisClient(redisHost, redisPort, redisUser,
                redisPassword, redisPoolConfig);
        try {
            String value = redisClient.get(namespace);
            return Long.parseLong(value);
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        } catch (Exception e) {
            throw new IdException.OperationFailedException(e);
        } finally {
            redisClient.close();
        }
    }
}