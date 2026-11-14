# [목적] 마스코트 v2에 화면별 모션 5종(홈 인사·출발 준비·잠시 쉬기·정산 축하·빈 화면 기다림)을 키프레임으로 만들고 투명 PNG로 렌더
# [이유] 앱의 모든 화면이 같은 '출발 회전' 한 가지만 재생해, 화면 상황에 맞는 동작이 필요함
# [사이드 임팩트] 원본 .blend는 건드리지 않고 새 파일(SAVE 경로)로 저장. 기존 4개 액션(손 흔들기 등)은 새 파일에서 지워짐
# [수정 시 고려사항] 실행: Blender -b models/2026-11-09-mascot-v2-four-actions.blend --python scripts/screen_actions.py -- <출력폴더> [still]
#   팔 축: 오른팔 Y 1.57=내림, 0 이하=들기 / 왼팔 Y 0=내림, 1.6 이상=들기. 눈 깜빡임은 scale.z .14→.02
#   모든 클립은 72프레임(24fps, 3초). 점프 높이를 올리면 카메라 shift_y(0.06)도 함께 조정할 것
import bpy, sys
args = sys.argv[sys.argv.index("--") + 1:]
out, still = args[0], len(args) > 1
O = bpy.data.objects
root, la, ra = O["MASCOT ROOT | move entire character"], O["CONTROL | left arm"], O["CONTROL | right arm"]
eyes = [O["05 | Eye 1"], O["05 | Eye -1"]]
for o in (root, la, ra, *eyes): o.animation_data_clear()
for a in list(bpy.data.actions): bpy.data.actions.remove(a)

CH = {  # 채널 이름 -> (오브젝트들, data_path, index, 기본값)
    "z": ([root], "location", 2, -0.02), "pitch": ([root], "rotation_euler", 0, 0), "roll": ([root], "rotation_euler", 1, 0),
    "yaw": ([root], "rotation_euler", 2, 0), "sz": ([root], "scale", 2, 1), "sxy": ([root], "scale", (0, 1), 1),
    "R": ([ra], "rotation_euler", 1, 1.57), "L": ([la], "rotation_euler", 1, 0), "eye": (eyes, "scale", 2, 0.14),
}
def blink(f): return [(f - 3, .14), (f, .02), (f + 3, .14)]
def squash(keys): return {"sz": keys, "sxy": [(f, 1 + (1 - v) * .5) for f, v in keys]}

CLIPS = {
 "home_greet": {  # 살짝 뛰어오른 뒤 손을 세 번 흔들어 인사
  "z": [(6, 0), (12, .35), (18, 0)], **squash([(1, 1), (6, .9), (11, 1.08), (18, .93), (23, 1.02), (28, 1)]),
  "R": [(14, 1.57), (22, -.15), (27, .3), (32, -.25), (37, .3), (42, -.25), (47, .3), (52, -.1), (62, 1.57)],
  "L": [(14, 0), (22, .25), (52, .25), (62, 0)], "roll": [(14, 0), (22, .08), (32, -.05), (42, .08), (52, -.03), (62, 0)],
  "yaw": [(8, 0), (20, .18), (52, .18), (64, 0)], "eye": blink(34) + blink(60)},
 "prep_ready": {  # 시동 걸듯 부르르 떨고, 좌우를 살핀 뒤 두 팔을 번쩍
  "z": [(1, 0), (4, .05), (7, 0), (10, .05), (13, 0), (16, .05), (19, 0), (47, 0), (52, .3), (57, 0)],
  **squash([(44, 1), (47, .92), (52, 1.06), (57, .94), (62, 1.02), (66, 1)]),
  "yaw": [(19, 0), (27, -.4), (33, -.4), (41, .4), (46, .4), (54, 0)],
  "L": [(46, 0), (52, 1.8), (58, 1.8), (68, 0)], "R": [(46, 1.57), (52, -.3), (58, -.3), (68, 1.57)], "eye": blink(30) + blink(64)},
 "pause_rest": {  # 눈이 스르르 감기고 몸을 낮춰 숨 쉬며 쉼 (감은 채로 끝남)
  "eye": [(1, .14), (10, .07), (16, .12), (30, .02), (72, .02)], **squash([(1, 1), (30, .94), (46, .98), (60, .94), (72, .96)]),
  "roll": [(1, 0), (30, .07), (72, .07)], "R": [(1, 1.57), (30, 1.75), (72, 1.75)]},
 "result_cheer": {  # 움츠렸다 한 바퀴 돌며 높이 뛰고, 두 팔 들고 한 번 더 콩
  "z": [(7, 0), (20, .65), (33, 0), (37, 0), (43, .25), (49, 0)],
  **squash([(1, 1), (7, .86), (12, 1.1), (30, 1.05), (34, .88), (40, 1.04), (45, .95), (50, 1)]),
  "yaw": [(7, 0), (33, 6.2832), (72, 6.2832)], "L": [(7, 0), (14, 1.9), (50, 1.9), (62, 0)],
  "R": [(7, 1.57), (14, -.35), (40, -.35), (43, 0), (46, -.35), (50, -.35), (62, 1.57)], "eye": [(12, .14), (16, .05), (52, .05), (58, .14)]},
 "empty_wait": {  # 고개를 갸웃하며 양쪽을 둘러보고 어깨를 으쓱
  "roll": [(1, 0), (12, .16), (28, .16), (40, -.12), (56, -.12), (66, 0)], "yaw": [(1, 0), (12, -.3), (28, -.3), (40, .35), (56, .35), (66, 0)],
  "R": [(30, 1.57), (40, .6), (56, .6), (66, 1.57)], "L": [(30, 0), (40, .7), (56, .7), (66, 0)],
  "z": [(38, 0), (42, .06), (46, 0)], "eye": blink(20) + blink(50)},
}
LEN = 72
s = bpy.context.scene
s.timeline_markers.clear()
for i, (name, clip) in enumerate(CLIPS.items()):
    start = i * LEN
    s.timeline_markers.new(name, frame=start + 1)
    for ch, (objs, path, idx, base) in CH.items():
        keys = clip.get(ch, [])
        # 클립 양 끝은 항상 기본 자세(쉬기 클립의 끝만 예외)로 고정해 클립끼리 섞이지 않게 함
        if not keys or keys[0][0] > 1: keys = [(1, 0 if ch == "z" else base)] + keys
        if keys[-1][0] < LEN: keys = keys + [(LEN, keys[-1][1])]
        for o in objs:
            for f, v in keys:
                v = v + base if ch == "z" else v
                for ix in (idx if isinstance(idx, tuple) else (idx,)):
                    getattr(o, path)[ix] = v
                    o.keyframe_insert(path, index=ix, frame=start + f)
s.frame_start, s.frame_end = 1, LEN * len(CLIPS)

r = s.render
s.camera = O["Camera | hero front three-quarter"]
s.camera.data.lens *= .8; s.camera.data.shift_y = .06   # 캐릭터를 아래로 내려 점프 머리 공간 확보
O["Ground | toggle for studio shadow"].hide_render = True
r.film_transparent = True; r.resolution_x = r.resolution_y = 512; r.resolution_percentage = 100
r.image_settings.file_format = 'PNG'; r.image_settings.color_mode = 'RGBA'
s.eevee.taa_render_samples = 16
if not still: bpy.ops.wm.save_as_mainfile(filepath=out + "/../../models/2026-11-12-mascot-v2-screen-actions.blend", copy=True)
for i, name in enumerate(CLIPS):
    for f in (range(6, LEN + 1, 11) if still else range(1, LEN + 1)):
        s.frame_set(i * LEN + f)
        r.filepath = f"{out}/{name}/{f:03d}.png"
        bpy.ops.render.render(write_still=True)
