package com.symphony.bdk.workflow.engine.executor;

import com.symphony.bdk.gen.api.model.Stream;
import com.symphony.bdk.gen.api.model.V3RoomAttributes;
import com.symphony.bdk.gen.api.model.V3RoomDetail;
import com.symphony.bdk.workflow.lang.swadl.activity.CreateRoom;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CreateRoomExecutor implements ActivityExecutor<CreateRoom> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateRoomExecutor.class);
  private static final String OUTPUT_ROOM_ID_KEY = "roomId";

  @Override
  public void execute(ActivityExecutorContext<CreateRoom> execution) {
    CreateRoom activity = execution.getActivity();
    List<Long> uids = activity.getUids();
    String name = activity.getName();
    String description = activity.getRoomDescription();
    boolean isPublic = activity.isPublic();

    final String createdRoomId;

    if (uids != null && !uids.isEmpty() && !StringUtils.isEmpty(name) && !StringUtils.isEmpty(description)) {
      createdRoomId = this.createRoom(execution, uids, name, description, isPublic);
      LOGGER.info("Stream {} created with {} users, id={}", name, uids.size(), createdRoomId);
    } else if (uids != null && !uids.isEmpty()) {
      createdRoomId = this.createRoom(execution, uids);
      LOGGER.info("MIM created with {} users, id={}", uids.size(), createdRoomId);
    } else {
      createdRoomId = this.createRoom(execution, name, description, isPublic);
      LOGGER.info("Stream {} created, id={}", name, createdRoomId);
    }

    execution.setOutputVariable(activity.getId(), OUTPUT_ROOM_ID_KEY, createdRoomId);
    execution.setVariable(activity.getId() + "." + EVENT, execution.getVariable(EVENT));

  }

  private String createRoom(ActivityExecutorContext execution, List<Long> uids) {
    Stream stream = execution.streams().create(uids);
    return stream.getId();
  }

  private String createRoom(ActivityExecutorContext execution, String name, String description, boolean isPublic) {
    V3RoomAttributes v3RoomAttributes = new V3RoomAttributes();
    v3RoomAttributes.name(name).description(description)._public(isPublic);
    V3RoomDetail v3RoomDetail = execution.streams().create(v3RoomAttributes);
    return v3RoomDetail.getRoomSystemInfo().getId();
  }

  private String createRoom(ActivityExecutorContext execution, List<Long> uids, String name, String description,
      boolean isPublic) {
    String roomId = createRoom(execution, name, description, isPublic);
    uids.forEach(uid -> execution.streams().addMemberToRoom(uid, roomId));
    return roomId;
  }
}
