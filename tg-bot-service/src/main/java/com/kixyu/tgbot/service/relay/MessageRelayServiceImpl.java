package com.kixyu.tgbot.service.relay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.pengrad.telegrambot.model.Message;

@Service
@RequiredArgsConstructor
class MessageRelayServiceImpl implements MessageRelayService {

    private final UserToGroupRelayForwarder userToGroupRelayForwarder;
    private final GroupToUserRelayForwarder groupToUserRelayForwarder;

    /**
     * 转发用户私聊消息到群话题，由内部转发器完成具体逻辑。
     *
     * @param privateMessage 用户私聊消息
     */
    @Override
    public void forwardPrivateMessageToGroupTopic(Message privateMessage) {
        userToGroupRelayForwarder.forward(privateMessage);
    }

    /**
     * 将群话题中的主人消息回流给用户，由内部转发器完成具体逻辑。
     *
     * @param groupMessage 群话题中的消息
     */
    @Override
    public void relayGroupTopicMessageToUser(Message groupMessage) {
        groupToUserRelayForwarder.relay(groupMessage);
    }
}
