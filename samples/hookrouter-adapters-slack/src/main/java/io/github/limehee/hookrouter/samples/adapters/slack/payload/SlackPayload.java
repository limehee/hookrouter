package io.github.limehee.hookrouter.samples.adapters.slack.payload;

import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class SlackPayload {

    @Nullable
    private String text;
    private List<LayoutBlock> blocks = new ArrayList<>();
    private List<Attachment> attachments = new ArrayList<>();

    public SlackPayload() {
    }

    public SlackPayload(@Nullable String text) {
        this.text = text;
    }

    private SlackPayload(
        @Nullable String text,
        List<LayoutBlock> blocks,
        List<Attachment> attachments
    ) {
        this.text = text;
        this.blocks = new ArrayList<>(blocks);
        this.attachments = new ArrayList<>(attachments);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String getText() {
        return text;
    }

    public void setText(@Nullable String text) {
        this.text = text;
    }

    public List<LayoutBlock> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<LayoutBlock> blocks) {
        this.blocks = new ArrayList<>(blocks);
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = new ArrayList<>(attachments);
    }

    public SlackPayload addBlock(LayoutBlock block) {
        this.blocks.add(block);
        return this;
    }

    public SlackPayload addBlocks(List<LayoutBlock> blockList) {
        this.blocks.addAll(blockList);
        return this;
    }

    public SlackPayload addAttachment(Attachment attachment) {
        this.attachments.add(attachment);
        return this;
    }

    @Override
    public String toString() {
        return "SlackPayload(text=" + text + ", blocks=" + blocks + ", attachments=" + attachments + ")";
    }

    public static final class Builder {

        private final List<LayoutBlock> blocks = new ArrayList<>();
        private final List<Attachment> attachments = new ArrayList<>();
        @Nullable
        private String text;

        private Builder() {
        }

        public Builder text(@Nullable String text) {
            this.text = text;
            return this;
        }

        public Builder block(LayoutBlock block) {
            this.blocks.add(block);
            return this;
        }

        public Builder blocks(List<LayoutBlock> blocks) {
            this.blocks.addAll(blocks);
            return this;
        }

        public Builder attachment(Attachment attachment) {
            this.attachments.add(attachment);
            return this;
        }

        public Builder attachments(List<Attachment> attachments) {
            this.attachments.addAll(attachments);
            return this;
        }

        public SlackPayload build() {
            return new SlackPayload(text, blocks, attachments);
        }
    }
}
