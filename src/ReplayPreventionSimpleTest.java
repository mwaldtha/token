import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ReplayPreventionSimpleTest {

    private static final int HITS_BEFORE_PURGE = Integer.parseInt(System.getProperty(TokenReplayPreventionImpl.HITS_BEFORE_PURGE_PROPERTY, "5"));
    private static final int TOKEN_COUNT = 5;  // convenience value for multiple token generation
    static TokenReplayPrevention replayPrevention = TokenReplayPreventionFactory.getInstance();
    
    public static void main(String[] args) {
            
        // Run single threaded functional tests 
        testExpiredToken();
        testSingleToken(); 
        testSingleton();
        testTokenPurge();
        
        // Add tokens using multiple threads with expired 'notValidAfter' values and expect all to not be replayed
        threadedTests(true, false);
        // Add tokens using multiple threads with non-expired 'notValidAfter' values and expect all to not be replayed
        threadedTests(false, false);
        // Add tokens using multiple threads with non-expired 'notValidAfter' values and expect all to be replayed
        threadedTests(false, true);
        // Add tokens using multiple threads with expired 'notValidAfter' values and expect all to be replayed
        threadedTests(true, true);
    }
    
    /**
     * Test the behavior of a token with an expired 'notValidAfter' value
     */
    private static void testExpiredToken() 
    {
        // Create a token with an already expired 'notValidAfter' value
        Token token = createTestToken("expired-token-ID", -20, -1);
        // The first check shouldn't be a replay because it is a new token
        doTest(token, false);
        // The second check shouldn't be a replay either since the 'notValidAfter' value was expired 
        doTest(token, false);
    }
    
    /**
     * Basic test of a single token
     */
    private static void testSingleToken()
    {
        // Create a test Token to test the TokenReplayPrevention
        Token token = createTestToken("single-token-ID", -20, 5);
        // The first check shouldn't be a replay because it's the first time TokenReplayPrevention object has seen it
        doTest(token, false);
        // The second check should be a replay since the same token is being submitted as before
        doTest(token, true);
    }
    
    /**
     * Test that the TokenReplayPrevention object retrieved from the factory is a singleton
     */
    private static void testSingleton()
    {
        // Get 2 instances of the TokenReplayPrevention object
        TokenReplayPrevention replayPrevention1 = TokenReplayPreventionFactory.getInstance();
        TokenReplayPrevention replayPrevention2 = TokenReplayPreventionFactory.getInstance();
        // Create a single test token
        Token token = createTestToken("singleton-token-ID", -20, 5);
        
        // Add the test token to each instance of the TokenReplayPrevention object
        // The first check shouldn't be a replay while the second one should
        assertTrue(!replayPrevention1.isTokenReplayed(token));
        assertTrue(replayPrevention2.isTokenReplayed(token));
    }
    
    /**
     * Test the purging of expired tokens
     */
    private static void testTokenPurge()
    {
        // Create a token with an already expired 'notValidAfter' value
        String tokenId = "purge-token-ID";
        Token token = createTestToken(tokenId, -20, -1);
        doTest(token, false);
        
        // Add enough tokens to invoke the purgeTokens method
        for (int x=0; x<HITS_BEFORE_PURGE; x++) {
            doTest(createTestToken("purge-buffer-token-ID" + x, -30, 10), false);
        }
        
        // Use the same tokenId as before but set the 'notValidAfter' value to the future
        token = createTestToken(tokenId, -20, 5);
        // First check should not be a replay since the previous entry should have been purged
        doTest(token, false);
        // Second check should be a replay since 'notValidAfter' is now in the future
        doTest(token, true);
    }
    
    
    /**
     * Tests a set of generated tokens using multiple threads
     * 
     * @param expired Should the token created by these tests have an expired 'notValdAfter' value
     * @param replayExpected Should the tokens created by these tests be expected to be replays
     */
    private static void threadedTests(boolean expired, final boolean replayExpected)
    {
        System.out.println("Starting threaded tests...");
        final ExecutorService executor = Executors.newCachedThreadPool();
        
        try {
            // Get generated set of tokens for this test
            Set<Token> tokens = getTestTokens(expired);
            
            // Loop through each generated token and call 'doTest' on thread for each
            for (final Token token : tokens) {
                Future<?> future = 
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            doTest(token, replayExpected);
                        }
                    });
                // Call to 'get' will throw ExecutionException if 'doTest' threw an exception in its thread, otherwise it is not needed
                future.get();
            }
            Thread.sleep(2000); //Pause at the end of each set of tests so multiple invocations don't step on each other
        } catch (InterruptedException e) {
            System.out.println("Thread was unable to sleep.");
        } catch (ExecutionException e) {
            // Throw exception from 'doTest' if there is a failure
            throw new RuntimeException("Test failed");
        } finally {
            executor.shutdown();
        }
    }
    
    /**
     * Utility method to test token and the expected result
     * 
     * @param token Token object to be tested
     * @param expected Should token be expected to be a replay
     * @throws RuntimeException
     */
    private static void doTest(Token token, boolean expected) throws RuntimeException
    {
        System.out.printf("Checking token %s, expecting replayed = %s%n", token.getTokenID(), expected);
        if (expected){
            assertTrue(replayPrevention.isTokenReplayed(token));
        } else {
            assertTrue(!replayPrevention.isTokenReplayed(token));
        }
    }

    /**
     * Utility test method
     * 
     * @param assertion
     */
    static void assertTrue(boolean assertion) {
        if (!assertion) {
            throw new RuntimeException("Assertion Failed");
        }
    }
    
    /**
     * Utility method for creating multiple test tokens at once
     * 
     * @param expired Should the generated tokens have an expired 'notValidAfter' value
     * @return Set<Token> A Set of Tokens
     */
    private static Set<Token> getTestTokens(boolean expired) {
        Set<Token> tokens = new HashSet<Token>();
        
        for (int x=0; x<TOKEN_COUNT; x++) {
            String tokenId = "test-token-" + x;
            
            tokens.add(
                createTestToken(tokenId, -20, (expired ? -10 : 5))
            );
        }
        
        return tokens;
    }
    
    /**
     * Utility method for creating individual test Tokens
     * 
     * @param tokenId Token identifier (key)
     * @param beforeOffset Amount to offset the 'notValidBefore' value (+/-) from the current time
     * @param afterOffset Amount to offset the 'notValidAfter' value (+/-) from the current time
     * @return Token object
     */
    private static Token createTestToken(String tokenId, int beforeOffset, int afterOffset) 
    {
        Calendar notBefore = Calendar.getInstance();
        notBefore.add(Calendar.SECOND, beforeOffset);
        Calendar notAfter = Calendar.getInstance();
        notAfter.add(Calendar.SECOND, afterOffset);
        
        return new Token(tokenId, notBefore.getTime(), notAfter.getTime(), null, tokenId.getBytes());
    }
}
