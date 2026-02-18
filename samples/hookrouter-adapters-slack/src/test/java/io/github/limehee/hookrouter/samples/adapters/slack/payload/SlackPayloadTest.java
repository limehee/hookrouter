package io.github.limehee.hookrouter.samples.adapters.slack.payload;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.PlainTextObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SlackPayloadTest {

    private SectionBlock createSectionBlock(String text) {
        return SectionBlock.builder()
            .text(PlainTextObject.builder().text(text).build())
            .build();
    }

    @Nested
    class ConstructorTest {

        @Test
        void shouldReturnNullPayloadText() {
            // When
            SlackPayload payload = new SlackPayload();

            // Then
            assertThat(payload.getText()).isNull();
            assertThat(payload.getBlocks()).isEmpty();
            assertThat(payload.getAttachments()).isEmpty();
        }

        @Test
        void shouldMatchExpectedPayloadText() {
            // When
            SlackPayload payload = new SlackPayload("test message");

            // Then
            assertThat(payload.getText()).isEqualTo("test message");
            assertThat(payload.getBlocks()).isEmpty();
            assertThat(payload.getAttachments()).isEmpty();
        }
    }

    @Nested
    class GetterSetterTest {

        @Test
        void shouldMatchExpectedPayloadTextWhenText() {
            // Given
            SlackPayload payload = new SlackPayload();

            // When
            payload.setText("new message");

            // Then
            assertThat(payload.getText()).isEqualTo("new message");
        }

        @Test
        void shouldHaveExpectedSizePayloadBlocks() {
            // Given
            SlackPayload payload = new SlackPayload();
            List<LayoutBlock> blocks = new ArrayList<>();
            blocks.add(createSectionBlock("test block"));

            // When
            payload.setBlocks(blocks);

            // Then
            assertThat(payload.getBlocks()).hasSize(1);
        }

        @Test
        void shouldHaveExpectedSizePayloadAttachments() {
            // Given
            SlackPayload payload = new SlackPayload();
            List<Attachment> attachments = new ArrayList<>();
            attachments.add(Attachment.builder().color("#ff0000").build());

            // When
            payload.setAttachments(attachments);

            // Then
            assertThat(payload.getAttachments()).hasSize(1);
        }
    }

    @Nested
    class AddBlockTest {

        @Test
        void shouldHaveExpectedSizeResult() {
            // Given
            SlackPayload payload = new SlackPayload();
            LayoutBlock block = createSectionBlock("block 1");

            // When
            SlackPayload result = payload.addBlock(block);

            // Then
            assertThat(result).isSameAs(payload);
            assertThat(payload.getBlocks()).hasSize(1);
        }

        @Test
        void shouldHaveExpectedSizePayloadBlocksWhenAddBlock() {
            // Given
            SlackPayload payload = new SlackPayload();

            // When
            payload.addBlock(createSectionBlock("block 1"))
                .addBlock(createSectionBlock("block 2"))
                .addBlock(createSectionBlock("block 3"));

            // Then
            assertThat(payload.getBlocks()).hasSize(3);
        }
    }

    @Nested
    class AddBlocksTest {

        @Test
        void shouldHaveExpectedSizeResultUsingFactoryMethod() {
            // Given
            SlackPayload payload = new SlackPayload();
            List<LayoutBlock> blocks = List.of(
                createSectionBlock("block 1"),
                createSectionBlock("block 2")
            );

            // When
            SlackPayload result = payload.addBlocks(blocks);

            // Then
            assertThat(result).isSameAs(payload);
            assertThat(payload.getBlocks()).hasSize(2);
        }
    }

    @Nested
    class AddAttachmentTest {

        @Test
        void shouldHaveExpectedSizeResultWhenColor() {
            // Given
            SlackPayload payload = new SlackPayload();
            Attachment attachment = Attachment.builder()
                .color("#ff0000")
                .text("attachment text")
                .build();

            // When
            SlackPayload result = payload.addAttachment(attachment);

            // Then
            assertThat(result).isSameAs(payload);
            assertThat(payload.getAttachments()).hasSize(1);
        }
    }

    @Nested
    class BuilderTest {

        @Test
        void shouldMatchExpectedPayloadTextWhenColor() {
            // Given
            LayoutBlock block = createSectionBlock("test block");
            Attachment attachment = Attachment.builder().color("#00ff00").build();

            // When
            SlackPayload payload = SlackPayload.builder()
                .text("test text")
                .block(block)
                .attachment(attachment)
                .build();

            // Then
            assertThat(payload.getText()).isEqualTo("test text");
            assertThat(payload.getBlocks()).hasSize(1);
            assertThat(payload.getAttachments()).hasSize(1);
        }

        @Test
        void shouldHaveExpectedSizePayloadBlocksUsingFactoryMethod() {
            // Given
            List<LayoutBlock> blocks = List.of(
                createSectionBlock("block 1"),
                createSectionBlock("block 2")
            );

            // When
            SlackPayload payload = SlackPayload.builder()
                .text("test")
                .blocks(blocks)
                .build();

            // Then
            assertThat(payload.getBlocks()).hasSize(2);
        }

        @Test
        void shouldReturnNullPayloadTextWhenBuilderHasNoFields() {
            // When
            SlackPayload payload = SlackPayload.builder().build();

            // Then
            assertThat(payload.getText()).isNull();
            assertThat(payload.getBlocks()).isEmpty();
            assertThat(payload.getAttachments()).isEmpty();
        }

        @Test
        void shouldHaveExpectedSizePayloadBlocksWhenAdd() {
            // Given
            List<LayoutBlock> originalBlocks = new ArrayList<>();
            originalBlocks.add(createSectionBlock("original"));

            SlackPayload payload = SlackPayload.builder()
                .blocks(originalBlocks)
                .build();

            // When
            originalBlocks.add(createSectionBlock("additional"));

            assertThat(payload.getBlocks()).hasSize(1);
        }
    }
}
