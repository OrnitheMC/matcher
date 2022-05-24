package matcher;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.exceptions.NoDiscordClientException;
import matcher.gui.Gui;
import matcher.type.ClassInstance;

import java.time.OffsetDateTime;

public class DiscordPresence
{
    private static final IPCClient CLIENT = new IPCClient(978074379382566973L);
    private static Gui matcher;
    private static ProjectType activeType = ProjectType.NO_PROJECT;
    private static OffsetDateTime startTime;

    public static void initialize(Gui gui) {
        matcher = gui;
        startTime = OffsetDateTime.now();

        CLIENT.setListener(new IPCListener()
        {
            @Override
            public void onReady(IPCClient client) {
                RichPresence.Builder builder = new RichPresence.Builder();
                builder.setDetails(activeType.getDescription())
                      .setStartTimestamp(startTime);
                client.sendRichPresence(builder.build());
            }
        });
        try {
            CLIENT.connect();
        } catch (NoDiscordClientException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setProject(ProjectType projectType) {
        activeType = projectType;
        startTime = OffsetDateTime.now();
        RichPresence.Builder builder = new RichPresence.Builder();
        builder.setDetails(activeType.getDescription())
              .setLargeImage(activeType.imageKey, activeType.description)
              .setStartTimestamp(startTime);
        CLIENT.sendRichPresence(builder.build());
    }

    public static void updateState() {
        RichPresence.Builder builder = new RichPresence.Builder();
        builder.setDetails(activeType.getDescription())
              .setLargeImage(activeType.imageKey, activeType.description)
              .setStartTimestamp(startTime);

        String dstClassName;
        if (matcher.getDstPane().getSelectedClass() != null) {
            dstClassName = ClassInstance.getClassName(matcher.getDstPane().getSelectedClass().getName());
        } else dstClassName = "null";

        builder.setState("Comparing classes " + ClassInstance.getClassName(matcher.getSrcPane().getSelectedClass().getName()) +
                " -> " + dstClassName);

        CLIENT.sendRichPresence(builder.build());
    }

    public static void close() {
        CLIENT.close();
    }

    public enum ProjectType
    {
        NO_PROJECT("No project running", null) {
            @Override
            public String getDescription() {
                return description;
            }
        },
        MATCHING("Matching", "matching") {
            @Override
            public String getDescription() {
                return description + " " +
                      matcher.getEnv().getInputFilesA() + " -> " +
                      matcher.getEnv().getInputFilesB();
            }
        },
        NESTING("Nesting", "nesting") {
            @Override
            public String getDescription() {
                return description + " " +
                      matcher.getEnv().getInputFilesA();
            }
        };

        public final String imageKey;
        protected final String description;

        ProjectType(String description, String imageKey) {
            this.description = description;
            this.imageKey = imageKey;
        }

        public abstract String getDescription();
    }
}
