package com.tradingplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_permissions")
public class UserPermissions {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "perm_dashboard",         nullable = false) private boolean dashboard        = true;
    @Column(name = "perm_market_overview",   nullable = false) private boolean marketOverview   = false;
    @Column(name = "perm_live_signals",      nullable = false) private boolean liveSignals      = true;
    @Column(name = "perm_positions",         nullable = false) private boolean positions        = true;
    @Column(name = "perm_trade_history",     nullable = false) private boolean tradeHistory     = true;
    @Column(name = "perm_orders",            nullable = false) private boolean orders           = false;
    @Column(name = "perm_risk_management",   nullable = false) private boolean riskManagement   = true;
    @Column(name = "perm_funds_margin",      nullable = false) private boolean fundsMargin      = false;
    @Column(name = "perm_broker_setup",      nullable = false) private boolean brokerSetup      = false;
    @Column(name = "perm_strategy_setup",    nullable = false) private boolean strategySetup    = true;
    @Column(name = "perm_strategy_settings", nullable = false) private boolean strategySettings = false;
    @Column(name = "perm_configuration",     nullable = false) private boolean configuration    = false;
    @Column(name = "perm_logs",              nullable = false) private boolean logs             = false;
    @Column(name = "perm_reports",           nullable = false) private boolean reports          = false;
    @Column(name = "perm_user_management",   nullable = false) private boolean userManagement   = false;

    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public boolean isDashboard() { return dashboard; }
    public void setDashboard(boolean v) { dashboard = v; }
    public boolean isMarketOverview() { return marketOverview; }
    public void setMarketOverview(boolean v) { marketOverview = v; }
    public boolean isLiveSignals() { return liveSignals; }
    public void setLiveSignals(boolean v) { liveSignals = v; }
    public boolean isPositions() { return positions; }
    public void setPositions(boolean v) { positions = v; }
    public boolean isTradeHistory() { return tradeHistory; }
    public void setTradeHistory(boolean v) { tradeHistory = v; }
    public boolean isOrders() { return orders; }
    public void setOrders(boolean v) { orders = v; }
    public boolean isRiskManagement() { return riskManagement; }
    public void setRiskManagement(boolean v) { riskManagement = v; }
    public boolean isFundsMargin() { return fundsMargin; }
    public void setFundsMargin(boolean v) { fundsMargin = v; }
    public boolean isBrokerSetup() { return brokerSetup; }
    public void setBrokerSetup(boolean v) { brokerSetup = v; }
    public boolean isStrategySetup() { return strategySetup; }
    public void setStrategySetup(boolean v) { strategySetup = v; }
    public boolean isStrategySettings() { return strategySettings; }
    public void setStrategySettings(boolean v) { strategySettings = v; }
    public boolean isConfiguration() { return configuration; }
    public void setConfiguration(boolean v) { configuration = v; }
    public boolean isLogs() { return logs; }
    public void setLogs(boolean v) { logs = v; }
    public boolean isReports() { return reports; }
    public void setReports(boolean v) { reports = v; }
    public boolean isUserManagement() { return userManagement; }
    public void setUserManagement(boolean v) { userManagement = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { updatedAt = v; }
}