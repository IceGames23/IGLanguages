package me.icegames.iglanguages.manager;

import me.icegames.iglanguages.IGLanguages;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.function.Consumer;

public class RedisManager {

    private final IGLanguages plugin;
    private JedisPool jedisPool;
    private final String channel = "iglanguages:update";
    private volatile boolean enabled = false;
    private Thread subscriberThread;
    private JedisPubSub pubSub;

    public RedisManager(IGLanguages plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        if (!plugin.getConfig().getBoolean("redis.enabled", false)) {
            return;
        }

        String host = plugin.getConfig().getString("redis.host", "localhost");
        int port = plugin.getConfig().getInt("redis.port", 6379);
        String password = plugin.getConfig().getString("redis.password", "");
        boolean useSsl = plugin.getConfig().getBoolean("redis.use-ssl", false);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);

        if (password == null || password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, useSsl);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password, useSsl);
        }

        this.enabled = true;
        plugin.getLogger().info("Redis connection initialized.");
    }

    public void publish(String message) {
        if (!enabled)
            return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to publish to Redis: " + e.getMessage());
            }
        });
    }

    public void subscribe(Consumer<String> callback) {
        if (!enabled)
            return;

        this.pubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                callback.accept(message);
            }
        };

        this.subscriberThread = new Thread(() -> {
            while (enabled && !Thread.currentThread().isInterrupted()) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.subscribe(pubSub, channel);
                } catch (Exception e) {
                    if (enabled) {
                        plugin.getLogger().warning("Redis subscription failed, reconnecting in 5s: " + e.getMessage());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }, "IGLanguages-RedisSubscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    public void close() {
        enabled = false;

        // Unsubscribe first
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {
            }
        }

        // Interrupt subscriber thread
        if (subscriberThread != null) {
            subscriberThread.interrupt();
            try {
                subscriberThread.join(5000);
            } catch (InterruptedException ignored) {
            }
        }

        // Close pool
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
