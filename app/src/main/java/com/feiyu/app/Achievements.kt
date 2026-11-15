package com.feiyu.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Achievement(
    val id: String,
    val icon: String,
    val title: String,
    val desc: String,
    val key: String,
    val target: Int = 1
)

object Achievements {

    val all = listOf(
        Achievement("gate", "🌸", "破碎的灵魂交织于无尽的花海", "回答出那句话", "gate"),
        Achievement("ask_name", "🐣", "韦一敏效应", "询问她的名字", "kw_name"),
        Achievement("first_launch", "🌍", "weight for the world", "第一次进入软件", "launch"),
        Achievement("cat", "🐱", "彩虹猫", "聊到猫", "kw_cat"),
        Achievement("week_chat", "🔥", "我也想成为正义的伙伴", "连续聊天一周", "streak", 7),
        Achievement("hide_seek", "🌼", "那天所看见的花的名字", "玩躲猫猫被找到", "kw_hide"),
        Achievement("photo", "🖼️", "原来外面的世界是这样子的啊，比我想象的还温柔。", "第一次发图片分享生活", "photo"),
        Achievement("save_world", "❄️", "赠予雪中的你", "说要拯救世界", "kw_save"),
        Achievement("love", "💗", "日常系的异能告白", "说出我爱你", "kw_love"),
        Achievement("game", "🎮", "你才是挑战者", "和机器人玩游戏输了", "kw_game"),
        Achievement("star_map", "🔍", "WhaleChat的名侦探", "第一次点开星图", "mapOpens"),
        Achievement("identity", "⭐", "就决定是你了", "第一次更改身份", "identity"),
        Achievement("export", "⏳", "神啊，请给我多一点时间", "第一次导出记忆", "exports"),
        Achievement("promise", "🌠", "星星的约定", "第一次与她产生约定", "kw_promise"),
        Achievement("anime", "📺", "次元の扉が開いた", "第一次和她聊动漫", "kw_anime"),
        Achievement("late_night", "🌙", "深夜食堂", "凌晨 0~4 点还在聊天", "kw_late"),
        Achievement("three_days", "🍀", "三日间的幸福", "连续使用 3 天", "streak", 3),
        Achievement("name", "💫", "你的名字。", "记忆累积到 100 条", "memories", 100),
        Achievement("violet", "💌", "紫罗兰永恒花园", "写下 10 篇日记", "diary", 10),
        Achievement("onepiece", "⚓", "海贼王", "累计 500 条消息", "messages", 500),
        Achievement("aot", "🗡️", "进击的巨人", "发一条超过 100 字的消息", "kw_long"),
        Achievement("demon_slayer", "🔥", "鬼灭之刃", "凌晨累计聊满 20 条", "kw_late", 20),
        Achievement("spirited", "🏮", "千与千寻", "打开记忆工作台", "memoryOpen"),
        Achievement("weathering", "☀️", "天气之子", "第一次设置自定义壁纸", "wallpaper"),
        Achievement("koe", "✉️", "声之形", "对她说谢谢", "kw_thanks"),
        Achievement("natsume", "🍂", "夏目友人帐", "累计使用 30 天", "daysUsed", 30),
        Achievement("mona_lisa", "🎨", "独属于我的蒙娜丽莎", "亲密度达到「唯一」", "intimacy", 6000)
    )

    private val keywords = listOf(
        listOf("名字", "叫什么", "你叫") to "kw_name",
        listOf("猫") to "kw_cat",
        listOf("躲猫猫", "捉迷藏") to "kw_hide",
        listOf("拯救世界") to "kw_save",
        listOf("我爱你", "喜欢你") to "kw_love",
        listOf("输了", "认输") to "kw_game",
        listOf("约定", "说好了", "答应我") to "kw_promise",
        listOf("动漫", "番剧", "动画", "二次元", "番") to "kw_anime",
        listOf("谢谢", "感谢", "多谢") to "kw_thanks"
    )

    fun prefs(ctx: Context) = ctx.getSharedPreferences("feiyu", Context.MODE_PRIVATE)

    fun stats(
        ctx: Context,
        messages: Int,
        memories: Int,
        diary: Int,
        feed: Int
    ): Map<String, Int> {
        val p = prefs(ctx)
        val map = HashMap<String, Int>()
        map["messages"] = messages
        map["memories"] = memories
        map["diary"] = diary
        map["feed"] = feed
        for (a in all) {
            if (!map.containsKey(a.key)) map[a.key] = p.getInt("stat_${a.key}", 0)
        }
        map["streak"] = p.getInt("stat_streak", 1)
        map["intimacy"] = Relationship.intimacy(ctx)
        return map
    }

    fun noteText(ctx: Context, text: String) {
        keywords.forEach { (words, key) ->
            if (words.any { text.contains(it) }) bump(ctx, key)
        }
        if (text.length >= 100) bump(ctx, "kw_long")
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour in 0..3) bump(ctx, "kw_late")
    }

    fun touchDaily(ctx: Context) {
        val p = prefs(ctx)
        bump(ctx, "launch")

        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val today = fmt.format(Date())
        val last = p.getString("stat_lastDay", "") ?: ""
        if (last == today) return

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = fmt.format(cal.time)

        val streak = if (last == yesterday) p.getInt("stat_streak", 0) + 1 else 1
        p.edit()
            .putString("stat_lastDay", today)
            .putInt("stat_streak", streak)
            .putInt("stat_daysUsed", p.getInt("stat_daysUsed", 0) + 1)
            .apply()
    }

    fun bump(ctx: Context, key: String) {
        val p = prefs(ctx)
        p.edit().putInt("stat_$key", p.getInt("stat_$key", 0) + 1).apply()
    }

    /** 返回本次新解锁的成就（每个只弹一次） */
    fun consumeNew(ctx: Context, stats: Map<String, Int>): List<Achievement> {
        val p = prefs(ctx)
        val fresh = ArrayList<Achievement>()
        for (a in all) {
            val value = stats[a.key] ?: 0
            if (value >= a.target && !p.getBoolean("ach_seen_${a.id}", false)) {
                p.edit().putBoolean("ach_seen_${a.id}", true).apply()
                fresh.add(a)
            }
        }
        return fresh
    }

    fun unlockedCount(stats: Map<String, Int>): Int =
        all.count { (stats[it.key] ?: 0) >= it.target }
}
