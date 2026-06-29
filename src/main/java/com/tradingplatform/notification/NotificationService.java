package com.tradingplatform.notification;

import com.tradingplatform.domain.BrokerAccount;
import com.tradingplatform.domain.Notification;
import com.tradingplatform.domain.Trade;
import com.tradingplatform.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void tradePlaced(BrokerAccount account, Trade trade) {
        save(account,
            "🟢 Trade Placed — " + trade.getIndexName(),
            trade.getTradingSymbol() + " | Entry: ₹" + trade.getEntryPrice()
                + " | Qty: " + trade.getQuantity()
                + " | SL: ₹" + trade.getStopLossPrice()
                + " | T1: ₹" + trade.getTarget1Price(),
            "TRADE_PLACED");
    }

    @Transactional
    public void target1Hit(BrokerAccount account, Trade trade, BigDecimal exitPrice, BigDecimal pnl) {
        save(account,
            "🎯 Target 1 Hit — " + trade.getIndexName(),
            trade.getTradingSymbol() + " | Exit: ₹" + exitPrice
                + " | P&L: " + formatPnl(pnl) + " | SL moved to cost",
            "TARGET1_HIT");
    }

    @Transactional
    public void target2Hit(BrokerAccount account, Trade trade, BigDecimal exitPrice, BigDecimal pnl) {
        save(account,
            "🏆 Target 2 Hit — " + trade.getIndexName(),
            trade.getTradingSymbol() + " | Exit: ₹" + exitPrice
                + " | P&L: " + formatPnl(pnl) + " | Full exit",
            "TARGET2_HIT");
    }

    @Transactional
    public void stopLossHit(BrokerAccount account, Trade trade, BigDecimal exitPrice, BigDecimal pnl) {
        save(account,
            "🔴 Stop Loss Hit — " + trade.getIndexName(),
            trade.getTradingSymbol() + " | Exit: ₹" + exitPrice
                + " | P&L: " + formatPnl(pnl),
            "STOP_LOSS");
    }

    @Transactional
    public void reEntryTriggered(BrokerAccount account, String indexName) {
        save(account,
            "🔄 Re-entry Triggered — " + indexName,
            "Conditions still valid after SL hit — new trade being placed",
            "REENTRY");
    }

    @Transactional
    public void squareOff(BrokerAccount account, String indexName, BigDecimal pnl) {
        save(account,
            "⏰ 3:15 PM Square-off — " + indexName,
            "Position closed at market price | P&L: " + formatPnl(pnl),
            "SQUARE_OFF");
    }

    @Transactional
    public void riskLimitHit(BrokerAccount account, String reason) {
        save(account,
            "⚠️ Risk Limit Hit",
            reason + " — No new trades for today",
            "RISK_LIMIT");
    }

    @Transactional
    public void signalGenerated(BrokerAccount account, String indexName, String symbol) {
        save(account,
            "📊 Signal Generated — " + indexName,
            "All entry conditions passed | Symbol: " + symbol,
            "SIGNAL");
    }

    @Transactional
    public void signalRejected(BrokerAccount account, String indexName, String reason) {
        save(account,
            "❌ Signal Rejected — " + indexName,
            "Entry conditions failed: " + reason,
            "SIGNAL_REJECTED");
    }

    private void save(BrokerAccount account, String title, String message, String type) {
        Notification n = new Notification();
        n.setBrokerAccount(account);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type);
        notificationRepository.save(n);
        log.info("[Notification][Account {}] {}", account.getId(), title);
    }

    private String formatPnl(BigDecimal pnl) {
        if (pnl == null) return "₹0.00";
        return (pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + "₹" + pnl.abs();
    }
}