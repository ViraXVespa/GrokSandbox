private com.vxv.chatbet.module.BetModule activeModule;

public void setActiveModule(com.vxv.chatbet.module.BetModule module) {
    if (this.activeModule != null) this.activeModule.onDeactivate();
    this.activeModule = module;
    if (module != null) module.onActivate();
}

public com.vxv.chatbet.module.BetModule getActiveModule() { return activeModule; }