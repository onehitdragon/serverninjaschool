package real;

import boardGame.Place;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import patch.Constants;
import patch.interfaces.TeamBattle;
import server.Service;
import server.util;
import threading.Manager;
import threading.Map;
import threading.Server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Battle {


    public static short MATCHING_WAIT_FOR_INFORMATION = 300;
    public static short MATCHING_WAIT_DURATION = 60;
    public static short MATCHING_DURATION = 10 * 60;

    public static long MIN_XU = 1000;
    public static int DAT_CUOC_STATE = 0;
    public static int DOI_1_PHUT_STATE = 1;
    public static int CHIEN_DAU_STATE = 2;
    public static int BATTLE_END_STATE = 3;

    public static int BATTLE_Y_RANGE_MAX = 264;
    public static int BATTLE_Y_RANGE_MIN = 240;
    @NotNull
    private static final AtomicInteger baseId = new AtomicInteger(0);
    @NotNull
    private TeamBattle team1;
    @NotNull
    private TeamBattle team2;
    private long xu1;
    private long xu2;
    private long finalXu = 0;
    public long startTime;
    private int state;
    private final int id;
    @NotNull
    public static ConcurrentHashMap<Integer, Battle> battles = new ConcurrentHashMap<>();
    private Place place;
    @Nullable
    private List<@NotNull Ninja> viewer;


    public Battle(final @NotNull Ninja ninja1,@NotNull Ninja ninja2) {


        this.team1 = ninja1.party == null ? ninja1 : ninja1.party;
        this.team2 = ninja2.party == null ? ninja2 : ninja2.party;
        viewer = new ArrayList<>();
        this.team1.setBattle(this);
        this.team2.setBattle(this);

        xu2 = 0;
        xu1 = 0;
        this.id = Battle.baseId.getAndIncrement();
        this.setState(DAT_CUOC_STATE);
        addBattle(this);
    }

    public int getState() {
        return this.state;
    }

    public void setState(int state) {
        this.tick();
        this.state = state;

        if (this.state == Battle.DAT_CUOC_STATE) {
            Service.batDauTinhGio(team1, MATCHING_WAIT_FOR_INFORMATION);
            Service.batDauTinhGio(team2, MATCHING_WAIT_FOR_INFORMATION);
        } else if (this.state == Battle.DOI_1_PHUT_STATE) {
            team1.updateEffect(new Effect(14, 0, MATCHING_WAIT_DURATION * 1000, 0));
            team2.updateEffect(new Effect(14, 0, MATCHING_WAIT_DURATION * 1000, 0));
            Service.batDauTinhGio(team1, 0);
            Service.batDauTinhGio(team2, 0);
        } else if (this.state == Battle.CHIEN_DAU_STATE) {
            Manager.serverChat("Server", team1.getTeamName() + " ( " + team1.getKeyLevel() + ") ??ang th??ch ?????u v???i " +
                    team2.getTeamName() + " (" + team2.getKeyLevel() + ") " + finalXu + " xu ??? l??i ????i.");
            team1.changeTypePk(Constants.PK_TRANG, team2);
            team2.changeTypePk(Constants.PK_DEN, team1);
            Service.batDauTinhGio(team1, MATCHING_DURATION);
            Service.batDauTinhGio(team2, MATCHING_DURATION);
            val msg = "Tr???n ?????u b???t ?????u";
            thongBao(msg);
        } else if (this.state == BATTLE_END_STATE) {
            Service.batDauTinhGio(team1, 0);
            Service.batDauTinhGio(team2, 0);

            val haruna = Server.getInstance().getMaps()[27];
            val place = haruna.getFreeArea();
            if (this.team1.getCurrentMapId() == 111) {
                this.team1.enterSamePlace(place, null);
            }

            if (this.team2.getCurrentMapId() == 111) {
                this.team2.enterSamePlace(place, null);
            }

            assert place != null;
            team1.changeTypePk(Constants.PK_NORMAL, team2);
            team2.changeTypePk(Constants.PK_NORMAL, team1);

            team1.enterSamePlace(place, this.team2);
            battles.remove(this.id);

            for (Ninja ninja : this.viewer) {
                ninja.enterSamePlace(place, null);
                ninja.isBattleViewer = false;
            }

            this.team1.clearBattle();
            this.team2.clearBattle();
            this.viewer.clear();
            this.viewer = null;
            this.team2 = null;
            this.team1 = null;
        }

    }


    public void setXu(long xu,final @NotNull TeamBattle team) {

        if (xu < MIN_XU) {
            team.notifyMessage("Xu ?????t t???i thi???u 1000 xu");
            return;
        }

        if (team == team1) {

            if (team.getXu() >= xu) {
                this.xu1 = xu;
            } else {
                team1.notifyMessage("Con kh??ng ????? xu ????? ?????t tr???n ?????u k???t th??c");
                team2.notifyMessage("?????i th??? kh??ng ????? xu ????? ?????t tr???n ?????u k???t th??c");
                setState(BATTLE_END_STATE);
                return;
            }
        } else if (team == team2) {
            if (team.getXu() >= xu) {
                this.xu2 = xu;

            } else {
                team1.notifyMessage("?????i th??? kh??ng ????? xu ????? ?????t tr???n ?????u k???t th??c");
                team2.notifyMessage("Con kh??ng ????? xu ????? ?????t tr???n ?????u k???t th??c");
                setState(BATTLE_END_STATE);
                return;
            }
        }


        String msg;
        if (this.canStart()) {
            // Start
            this.start();
            msg = "C??c con c?? 1 ph??t ????? chu???n b??? cho tr???n ?????u ";
        } else {
            msg = team.getTeamName() + " ???? thay ?????i s??? ti???n ?????t c?????c l?? " + xu;
        }
        thongBao(msg);
    }

    private void thongBao(String msg) {
        this.team1.notifyMessage(msg);
        this.team2.notifyMessage(msg);
    }


    public long getFinalXu() {
        return finalXu;
    }

    public boolean canStart() {
        return this.xu1 == this.xu2 && !this.isExpired();
    }

    public void start() {


        this.finalXu = this.xu1;

        Server.getInstance();
        Map loiDai = Server.getMapById(111);
        this.place = loiDai.getFreeArea();
        if (place != null) {
            team1.enterSamePlace(place, this.team2);
        } else {
            thongBao("Hi???n t???i l??i ????i ??ang qu?? t???i # quay th??? l???i sau nh??");
        }
        this.tick();
        this.setState(DOI_1_PHUT_STATE);

        Server.executorService.submit(() -> {
            try {
                Thread.sleep(MATCHING_WAIT_DURATION * 1000);
                this.setState(CHIEN_DAU_STATE);
                util.Debug("STOP COUNT DOWN BATTLE");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    public void tick() {
        this.startTime = System.currentTimeMillis();
    }


    public boolean isExpired() {
        long timeLimit;

        if (state == Battle.DOI_1_PHUT_STATE) {
            timeLimit = MATCHING_WAIT_DURATION;
        } else if (state == Battle.CHIEN_DAU_STATE) {
            timeLimit = MATCHING_DURATION;
        } else if (state == Battle.DAT_CUOC_STATE) {
            timeLimit = MATCHING_WAIT_FOR_INFORMATION;
        } else {
            throw new RuntimeException("State undefined");
        }

        return System.currentTimeMillis() - this.startTime > timeLimit * 1000;
    }

    public static void addBattle(final @NotNull Battle battle) {
        battles.put(battle.id, battle);
    }

    public synchronized void updateWinner(@NotNull final TeamBattle team) {

        TeamBattle winner = team == team1 ? team2 : team1;
        TeamBattle looser = team == team1 ? team1 : team2;


        looser.notifyMessage("# ???? b??? " + winner.getTeamName() + " ????nh b???i");
        looser.upXuMessage(-getFinalXu());

        winner.upXuMessage(getFinalXu());
        for (Ninja ninja : winner.getNinjas()) {
            if (ninja != null && ninja.getTaskId() == 43 && ninja.getTaskIndex() == 1) {
                ninja.upMainTask();
            }
        }
        winner.notifyMessage("# ???? ????nh b???i " + looser.getTeamName() + " v?? nh???n ???????c " + getFinalXu());

        this.setState(BATTLE_END_STATE);
    }

    public void enter() {
        Server.getInstance();
        Map waitingMap = Server.getMapById(110);
        Place freeArea = waitingMap.getFreeArea();
        if (freeArea == null) {
            this.team1.notifyMessage("Hi???n t???i ph??ng ch??? ????ng c??n ????ng l???m con quay l???i sau");
            this.team2.notifyMessage("Hi???n t???i ph??ng ch??? ????ng c??n ????ng l???m con quay l???i sau");
        }
        this.team1.enterSamePlace(freeArea, this.team2);
    }

    @NotNull
    public String getTeam1Name() {
        return this.team1.getTeamName() + " (" + team1.getKeyLevel() + ")";
    }

    @NotNull
    public String getTeam2Name() {
        return this.team2.getTeamName() + " (" + team2.getKeyLevel() + ")";
    }

    public Place getPlace() {
        return this.place;
    }

    public void addViewerIfNotInMatch(Ninja ninja) {
        if (ninja != team1 && ninja != team2) {
            this.viewer.add(ninja);
        }
    }
}
