public class TokenReplayPreventionFactory
{
    private static TokenReplayPrevention singletonInstance = new TokenReplayPreventionImpl();

    static TokenReplayPrevention getInstance()
    {
        return singletonInstance;
    }
}
