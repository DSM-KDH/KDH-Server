package kdh.domain.routine.enum

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "사용 가능한 운동 기구. BARBELL=바벨, DUMBBELL=덤벨, CABLE=케이블, MACHINE=머신, BENCH=벤치, PULL_UP_BAR=철봉, BAND=밴드, LAT_PULL_DOWN=랫풀다운 머신, SMITH_MACHINE=스미스 머신, LEG_PRESS=레그 프레스, PEC_DECK_FLY=펙덱 플라이 머신, DIP_STATION=딥스 스테이션, FOAM_ROLLER=폼롤러, CYCLE=사이클")
enum class EquipmentType {
    BARBELL, DUMBBELL, CABLE, MACHINE, BENCH, PULL_UP_BAR,
    BAND, LAT_PULL_DOWN, SMITH_MACHINE, LEG_PRESS,
    PEC_DECK_FLY, DIP_STATION, FOAM_ROLLER, CYCLE
}
