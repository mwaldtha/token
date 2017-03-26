import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenReplayPreventionImpl implements TokenReplayPrevention {

    public static final String HITS_BEFORE_PURGE_PROPERTY = "hits_before_purge"; 
    // Value to tune how often the purge of expired tokens takes place
    private static final int HITS_BEFORE_PURGE = Integer.parseInt(System.getProperty(HITS_BEFORE_PURGE_PROPERTY, "5"));
    
    private static final ConcurrentHashMap<String, Token> tokenCache = new ConcurrentHashMap<String, Token>();
    private static int hitCount = 0;
    private static final Object lock = new Object();
    
    /**
     * Detect whether the given token is a replay or not.
     *
     * @param token the token that will be checked to ensure that it hasn't been used previously
     * @return true if it's a replay
     */
    public boolean isTokenReplayed(Token token) {
        boolean replayed = true;
        String tokenId = token.getTokenID();
        
        System.out.printf("Checking for replay of %s on thread %s%n", tokenId, Thread.currentThread().getName());
        // Put token in the cache if it's not there already
        Token existingToken = tokenCache.putIfAbsent(tokenId, token);
        
        // If token is in map, check expiration date
        if (existingToken != null) {
            System.out.printf("Token %s was found in the cache on thread %s%n", tokenId, Thread.currentThread().getName());
            // Allow token to be replaced if entry is in map but expired
            if (existingToken.getNotValidAfter().before(new Date())) {
                System.out.printf("Found token %s was expired in the cache on thread %s%n", tokenId, Thread.currentThread().getName());
                replayed = !tokenCache.replace(tokenId, existingToken, token);
            }
        } else {
            replayed = false;
        }

        // Synchhronize block to ensure proper hitCount so purge happens after every HITS_BEFORE_PURGE invocations
        synchronized(lock) {
            if (hitCount++ == HITS_BEFORE_PURGE) {
                System.out.printf("Purging expired tokens on thread %s%n", Thread.currentThread().getName());
                purgeExpiredTokens();
                hitCount = 0;
            }
        }
        
        return replayed;
    }
    
    /**
     * Iterate over the token cache and remove any expired tokens
     */
    private void purgeExpiredTokens() {
        for (Token token : tokenCache.values()) {
            if (token.getNotValidAfter().before(new Date())) {
                tokenCache.remove(token.getTokenID(), token);
                System.out.printf("Purged token %s on thread %s%n", token.getTokenID(), Thread.currentThread().getName());
            }
        }
    }

}
