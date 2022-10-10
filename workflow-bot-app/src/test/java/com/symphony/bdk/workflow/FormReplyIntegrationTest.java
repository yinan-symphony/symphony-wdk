package com.symphony.bdk.workflow;

import static com.symphony.bdk.workflow.custom.assertion.Assertions.assertThat;
import static com.symphony.bdk.workflow.custom.assertion.WorkflowAssert.contains;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.symphony.bdk.core.service.message.model.Message;
import com.symphony.bdk.gen.api.model.V4Message;
import com.symphony.bdk.workflow.swadl.SwadlParser;
import com.symphony.bdk.workflow.swadl.exception.ActivityNotFoundException;
import com.symphony.bdk.workflow.swadl.v1.Workflow;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

class FormReplyIntegrationTest extends IntegrationTest {

  @Test
  void sendFormSendMessageOnReply() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));

    // reply to form
    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("aField", "My message")));
      // bot should send back my reply
      verify(messageService, atLeast(1)).send(eq("123"), contains("My message"));
      return true;
    });
  }

  @Test
  void sendFormSendMultiLinesReply() throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply-multi-lines.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));


    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    // reply to form
    await().atMost(50, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "sendForm",
          Collections.singletonMap("content", StringEscapeUtils.unescapeJava("A\nB\nC\rD"))));
      // bot should send back my reply
      verify(messageService, atLeast(1)).send(eq("1234"), captor.capture());
      return true;
    });

    assertThat(captor.getValue().getContent()).isEqualTo("<messageML>\n  A\nB\nC\nD\n</messageML>\n");
    assertThat(workflow).executed("sendForm", "reply");
  }

  @Test
  void sendFormSendMessageOnReply_multipleUsers() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply.swadl.yaml"));
    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      // user 1 replies to form
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("aField", "My message")));
      // user 2 replies to form
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("aField", "My message")));

      // bot should send back my reply
      verify(messageService, atLeast(2)).send(eq("123"), contains("My message"));
      return true;
    });
  }

  @Test
  void sendFormSendMessageOnReply_followUpActivity() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass()
        .getResourceAsStream("/form/send-form-reply-followup-activity.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      // user 1 replies to form
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("aField", "My message")));

      // bot should send back my reply
      verify(messageService, timeout(5000)).send(eq("123"), contains("First reply: My message"));
      verify(messageService, timeout(5000)).send(eq("123"), contains("Second reply: My message"));

      return true;
    });
  }

  @Test
  void sendFormSendMessageOnReply_followUpActivity_exclusive() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass()
        .getResourceAsStream("/form/send-form-reply-followup-activity-exclusive.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      // user 1 replies to form
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("aField", "My message")));

      // bot should send back my reply
      verify(messageService, timeout(5000)).send(eq("123"), contains("First reply: My message"));
      verify(messageService, timeout(5000)).send(eq("123"), contains("Second reply: My message"));

      return true;
    });
  }

  @Test
  void sendFormSendMessageOnReply_expiration() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(
        getClass().getResourceAsStream("/form/send-form-reply-expiration.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));
    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));

    // user never replies

    // bot should run the on/activity-expired activity after 1s
    verify(messageService, timeout(5000)).send(eq("123"), contains("Form expired"));
  }

  @Test
  void sendFormUniqueReply_timeout() throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-unique-reply-timeout.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));
    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/send"));

    // make the form expires
    sleepToTimeout(500);

    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));
    assertThat(workflow).as("The form was sent but the reply activity timed out")
        .executed("sendForm")
        .notExecuted("replyWithTimeout");
  }

  @Test
  void sendFormUniqueReply_timeout_followingActivities() throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(
            getClass().getResourceAsStream("/form/send-form-unique-reply-timeout-following-activities.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));
    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/send"));

    // make the form expires
    sleepToTimeout(500);

    verify(messageService, timeout(5000)).send(eq("123"), contains("form"));
    assertThat(workflow)
        .as("The form was sent but the reply activity timed out. "
            + "Consequently, the activities in the main flow was not executed but the ones in timeout branch were")
        .executed("sendForm", "scriptInTimeoutFlow", "followingScriptInTimeoutFlow")
        .notExecuted("replyWithTimeout", "scriptInMainFlow");
  }

  @Test
  void sendFormNested() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass().getResourceAsStream(
        "/form/send-form-reply-nested.swadl.yaml"));
    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("abc"), contains("form"));

    // reply to first form
    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("question", "My message")));
      return true;
    });

    // bot should not run the on/activity-expired activity after 1s because it is attached to the second form that
    // has not been replied to
    verify(messageService, timeout(5000)).send(eq("abc"), contains("expiration-outer"));
  }

  @SuppressWarnings("checkstyle:LineLength")
  @Test
  void sendFormInvalidFormId() throws Exception {
    final Workflow workflow = SwadlParser.fromYaml(
        getClass().getResourceAsStream("/form/invalid/swadl/send-form-reply-unknown-activity-id.swadl.yaml"));

    assertThatExceptionOfType(ActivityNotFoundException.class)
        .isThrownBy(() -> engine.deploy(workflow))
        .satisfies(e -> assertThat(e.getMessage()).isEqualTo(
            "Invalid activity in the workflow send-form-reply-invalid-activity-id: No activity found with id unknownActivityId referenced in pongReply"));
  }

  @Test
  void sendFormLoop() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-loop.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("ABC"), contains("form"));

    // reply to form
    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("action", "reject")));
      // bot should send my form back because reject was selected
      verify(messageService, timeout(5000).times(2)).send(eq("ABC"), contains("form"));
      return true;
    });
  }

  @Test
  void sendFormContinuation() throws Exception {
    Workflow workflow = SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-continuation.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/message"));
    verify(messageService, timeout(5000)).send(eq("ABC"), contains("form"));

    // reply to form
    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("action", "reject")));
      // bot should send my form back because reject was selected
      verify(messageService, timeout(5000)).send(eq("ABC"), contains("afterReply1"));
      return true;
    });
  }

  @Test
  void sendFormOutputsArePreserved() throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-outputs-are-preserved.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/run_form_outputs_preserved"));
    verify(messageService, timeout(5000)).send(eq("ABC"), contains("form"));

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "init", Collections.singletonMap("action", "one")));
      return true;
    });

    assertThat(workflow).executed("init", "check");
  }

  @Test
  void sendFormUpdateMessage() throws Exception {
    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));
    V4Message message = new V4Message();
    message.setMessage("<presentationML>/hey</presentationML>");
    message.messageId("msgId");
    when(messageService.getMessage(anyString())).thenReturn(message);
    when(messageService.update(any(V4Message.class), any(Message.class))).thenReturn(message);

    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply-update-message.swadl.yaml"));
    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/init"));
    verify(messageService, timeout(5000)).send(anyString(), contains("form"));
    clearInvocations(messageService);
    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
      engine.onEvent(form("msgId", "init", Collections.singletonMap("action", "x")));
      assertThat(workflow).executed("init", "update");
    });
  }

  @Test
  void sendMessageUpdateMessage() throws Exception {
    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));
    V4Message message = new V4Message();
    message.setMessage("<presentationML>/hey</presentationML>");
    message.messageId("msgId");
    when(messageService.getMessage(anyString())).thenReturn(message);
    when(messageService.update(any(V4Message.class), any(Message.class))).thenReturn(message);

    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply-update-message.swadl.yaml"));
    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/init"));
    verify(messageService, timeout(5000)).send(anyString(), contains("form"));
    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
      engine.onEvent(messageReceived("/hey"));
      assertThat(workflow).executed("init", "update");
    });
  }

  @Test
  void formRepliedSendMessageOnConditionIf() throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply-conditional.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/test"));
    verify(messageService, timeout(5000)).send(anyString(), contains("form"));
    clearInvocations(messageService);

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "testForm", Collections.singletonMap("action", "create")));
      return true;
    });
    verify(messageService, timeout(5000)).send(anyString(), contains("Create"));

    Thread.sleep(1000);
    assertThat(workflow).executed(workflow, "testForm", "resCreate");
  }

  @Test
  void formRepliedSendMessageOnConditionElse() throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply-conditional.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/test"));
    verify(messageService, timeout(5000)).send(anyString(), contains("form"));
    clearInvocations(messageService);

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "testForm", Collections.singletonMap("action", "menu")));
      return true;
    });
    verify(messageService, timeout(5000)).send(anyString(), contains("Menu"));
    clearInvocations(messageService);

    sleepToTimeout(1000);
    engine.onEvent(messageReceived("/continue"));
    verify(messageService, timeout(5000)).send(anyString(), contains("DONE"));

    assertThat(workflow).executed(workflow, "testForm", "resMenu", "finish");
  }

  @ParameterizedTest
  @CsvSource(value = {"GOOG,response0", "GOOGLE,response1"})
  void formReplied_fork_condition_join_activity(String tickerValue, String expectedActivity) throws Exception {
    Workflow workflow =
        SwadlParser.fromYaml(getClass().getResourceAsStream("/form/send-form-reply-join-activity.swadl.yaml"));

    when(messageService.send(anyString(), any(Message.class))).thenReturn(message("msgId"));

    engine.deploy(workflow);

    // trigger workflow execution
    engine.onEvent(messageReceived("/go"));
    verify(messageService, timeout(2000)).send(anyString(), contains("form"));
    clearInvocations(messageService);

    await().atMost(5, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
      engine.onEvent(form("msgId", "sendForm", Collections.singletonMap("ticker", tickerValue)));
      return true;
    });
    verify(messageService, timeout(2000)).send(anyString(), contains("END"));

    assertThat(workflow).executed(workflow, "sendForm", expectedActivity, "response2");
  }
}
