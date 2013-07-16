@PostConstruct
protected void initBean(){
}

public void continueOrInitConversation() {
        if (conversation.isTransient()) {
            conversation.begin();
            logger.trace("conversation with id {} has started by {}.", conversation.getId(), getClass().getName());
        }
    }

public void endConversationIfContinuing() {
        if (!conversation.isTransient()) {
            logger.trace("conversation with id {} has ended by {}.", conversation.getId(), getClass().getName());
            conversation.end();
        }
}
