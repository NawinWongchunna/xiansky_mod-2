# Ascension Rings (Fabric 1.20.1)

มี 3 สกิล: **P = Ascension** (ลอยขึ้นฟ้าพร้อมวงแหวนไฟ), **G = Void Blade** (แหวกมิติเรียกกระบี่ดำปักพื้น ทำลายรอบ 100 บล็อก พร้อมพายุ) และ **J = Yin-Yang Scripture** (คัมภีร์หยินหยาง + มังกรขาว/ดำ)

กดปุ่ม **P** → ลอยขึ้นฟ้าพร้อมวงแหวนไฟ → วงเวทย์กางใต้เท้า ดอกเถาวัลย์แดงผลิบาน → ดูดพลังรอบๆ เข้าแกนวอร์เท็กซ์ ท้องฟ้าเปลี่ยนเป็นสีแดงเข้ม กล้องสั่นหนักขึ้น → flash ขาว ปล่อยพลังออกรอบตัว (ดาเมจ + ไฟ + knockback) แล้วลอยลงช้าๆ

ความยาวทั้งหมด 160 tick (8 วินาที) — แผน A: Cinematic Ascension

## สกิล G — Void Blade (ยกมือแหวกมิติ เรียกกระบี่ดำ)
ใช้เวลา 21 วินาที (420 tick):

| tick | เหตุการณ์ |
|---|---|
| 0-25 | ยกมือขึ้นฟ้า พายุฝนฟ้าคะนองก่อตัว ท้องฟ้ามืดม่วงดำ |
| 25-75 | รอยแยกมิติขนาด 80 บล็อกเปิดบนฟ้า (สูง 100 บล็อกเหนือจุดปัก) |
| 75-150 | กระบี่ดำค่อยๆ โผล่ออกจากรอยแยก ยาวราว 60 บล็อก มีออร่าปีศาจ |
| 150-175 | กระบี่ลอยนิ่งสั่นสะเทือน ฟ้าผ่ารัวขึ้น กล้องสั่นหนัก |
| 175-190 | กระบี่พุ่งปักพื้น (จุดปัก = หน้าผู้เล่น 12 บล็อก) |
| 190 | แฟลชขาว คลื่นทำลายล้างแผ่ออกรัศมี 100 บล็อกใน 5 วินาที: ทำลายบล็อก (ลึกที่สุดตรงกลาง), ดาเมจ + ไฟ + knockback, เผาพื้นเป็น blackstone/magma/crying obsidian |
| 360-420 | กระบี่จางหาย พายุสงบ (คืนสภาพอากาศเดิม) |

- ผู้ร่ายได้ Resistance + Slow Falling ระหว่างสกิล และมีเสาพื้นเล็กๆ ใต้เท้าที่ไม่ถูกทำลาย
- คลื่นทำงานเฉพาะ chunk ที่โหลดอยู่ (ถ้า render/simulation distance น้อยกว่า 100 บล็อก คลื่นจะไปสุดเท่าที่โหลด)
- ทดสอบด้วยคำสั่ง `/voidblade` (ต้อง OP)
- **คำเตือน:** สกิลนี้ทำลายพื้นที่จริง ควร backup โลกก่อน หรือตั้ง `destroyBlocks` เป็น false ใน config

## สกิล J — Yin-Yang Scripture (คัมภีร์หยินหยาง + มังกรหยินหยาง)
**กด J ครั้งที่ 1** (ไม่ล็อกการควบคุม ไม่มีกล้องสั่น):

| tick | เหตุการณ์ |
|---|---|
| 0-24 | ยกมือโยนคัมภีร์ขึ้นฟ้า |
| 24-64 | คัมภีร์ขยายใหญ่ (กว้างราว 48 บล็อก) ลอยไปอยู่ **ด้านหลังผู้เล่น** สูง 34 บล็อก หันหน้าเข้าหาผู้เล่น |
| 36-84 | คัมภีร์เปิดหน้า (หน้าขาวอักขระดำ / หน้าดำอักขระทอง) |
| 92-162 | มังกรขาวพุ่งออกจากหน้าคัมภีร์แบบ "โผล่ขึ้นจากน้ำ": วงน้ำกระเพื่อมที่ปากรู หยดน้ำกระเด็นเข้าหาผู้เล่น ตัวมังกรค่อยๆ ดึงออกมาทีละช่วงจากหัวไปหาง |
| 122-192 | มังกรดำพุ่งออกตามมา |
| 200+ | มังกรบินโคจรรอบผู้เล่น รอคำสั่ง (ไม่กดภายใน 90 วินาที สกิลจบเอง) |

**กด J ครั้งที่ 2** (เล็งไปที่ไหนก็ได้ ไกลสุด ~160 บล็อก):
- มังกรทั้งสองพุ่งเป็นเส้นโค้งไปยังจุดที่เล็ง (มีเงาตามหลัง) ใช้เวลา 36 tick
- ระเบิดหยินหยางขาว-ดำ + เสาแสงเกลียว + แฟลร์ + แฟลชจอ
- คลื่นทำลายล้างแผ่รัศมี 100 บล็อกใน 4 วินาที: ทำลาย **ทุกอย่าง** (ต้นไม้ ใบไม้ หญ้า ตึก และพื้นลึกตามระยะ) ดาเมจ 200 + knockback
- พื้นที่ถูกเปลี่ยนเป็น **ลายก้นหอยหยินหยางขาว-ดำ** (white/black concrete) ท้องฟ้าเป็นโทนเทาขาวดำชั่วคราว
- เสียงทั้งหมดเป็นเสียงต้นฉบับที่สังเคราะห์เอง (`assets/ascension_rings/sounds/yy_*.ogg`) ไม่ใช้เสียงจากเกม
- ทดสอบด้วยคำสั่ง `/yinyang` (ต้อง OP: เรียกครั้งแรก = เริ่ม, เรียกอีกครั้งหลัง 10 วินาที = สั่งโจมตี)
- **คำเตือน:** สกิลนี้ทำลายพื้นที่จริงรัศมี 100 บล็อก ควร backup โลกก่อน หรือตั้ง `destroyBlocks` เป็น false (ยังได้เห็นเอฟเฟกต์ครบแต่ไม่ทำลายบล็อก)

## Requirements
- Minecraft **1.20.1**, Fabric Loader **0.20.1** (รองรับ >= 0.15), Fabric API (0.92.x สำหรับ 1.20.1), Java 17

## Build
```
gradle build          # หรือ  gradle wrapper && ./gradlew build
```
ไฟล์ jar อยู่ที่ `build/libs/ascension-rings-1.0.0.jar` ใส่ในโฟลเดอร์ `mods`
ทุกครั้งที่ push ขึ้น GitHub, workflow `.github/workflows/build.yml` จะ build jar ให้อัตโนมัติ (ดูที่แท็บ Actions → Artifacts) และถ้า push tag `v*` จะสร้าง Release พร้อมแนบ jar

## ควบคุม
- ปุ่ม **P** (เปลี่ยนได้ที่ Options → Controls → "Ascension Rings") หรือพิมพ์ `/ascension` ในแชท (ต้องเป็น OP/เปิด cheats)
  - vanilla ใช้ P เป็น Social Interactions: ม็อดจะย้าย Social Interactions ออกจาก P ให้อัตโนมัติ
- ระหว่างสกิลเดิน/กระโดดไม่ได้ และผู้เล่นได้ Resistance 5 กันตาย

## Config — `config/ascension_rings.json`
| key | ค่าเริ่มต้น | ความหมาย |
|---|---|---|
| `cinematic` | true | เปิด cutscene (กล้อง orbit/สั่น/FOV, ฟ้าแดง, แถบดำ, overlay) |
| `damage` | 14.0 | ดาเมจตอนปล่อยพลัง |
| `radius` | 14.0 | รัศมีดาเมจ |
| `cooldownTicks` | 400 | คูลดาวน์ (20 tick = 1 วิ) |
| `swordRadius` | 100.0 | รัศมีคลื่นทำลายล้างสกิล G |
| `swordDamage` | 80.0 | ดาเมจสกิล G |
| `swordCooldownTicks` | 2400 | คูลดาวน์สกิล G (2 นาที) |
| `destroyBlocks` | true | ทำลายบล็อกหรือไม่ |
| `changeWeather` | true | เปลี่ยนเป็นพายุฝนฟ้าคะนอง |
| `restoreWeather` | true | คืนสภาพอากาศเดิมหลังจบ |
| `swordLightning` | true | ฟ้าผ่าประกอบฉาก (cosmetic) |
| `yinYangRadius` | 100.0 | รัศมีระเบิดสกิล J |
| `yinYangDamage` | 200.0 | ดาเมจสกิล J |
| `yinYangCooldownTicks` | 3600 | คูลดาวน์สกิล J (3 นาที) |
| `yinYangPaintGround` | true | ทาพื้นเป็นลายหยินหยางขาว-ดำหลังทำลาย |

## Multiplayer / Server
- ใช้ได้ทั้ง singleplayer และ server (mod ต้องติดตั้งทั้งสองฝั่ง)
- ผู้เล่นคนอื่นที่เห็นเราอยู่จะเห็นวงแหวน วงเวทย์ ดอกไม้ ลูกพลัง ส่วน cutscene/ฟ้าแดงเห็นเฉพาะคนกด
- Dedicated server ควรตั้ง `allow-flight=true` ใน `server.properties` ไม่เช่นนั้นระบบกัน fly อาจเตะผู้เล่นที่ลอยนานเกิน ~5 วินาที

## โครงสร้าง
```
src/main/java/dev/ascension/rings/
├── AscensionMod.java          entrypoint (server+common)
├── AscensionConfig.java
├── Timeline.java              ไทม์ไลน์ + ความเร็วลอย (ใช้ร่วม)
├── network/Net.java           packet id
├── ability/AscensionServer.java   สถานะ/เสียง/ดาเมจ/คูลดาวน์
├── client/AscensionClient.java    keybind, event
├── client/ClientState.java        สถานะ client, ขับการลอย, particle
├── client/camera/CameraEffects.java  keyframe กล้อง/FOV/สั่น
├── client/vfx/AscensionRenderer.java วงแหวน/วงเวทย์/ดอกไม้/วอร์เท็กซ์/orb
├── client/vfx/AscensionHud.java      แถบดำ, vignette, flash, overlay ดอกไม้
└── mixin/                     Camera, GameRenderer(FOV), ClientWorld(ฟ้า/เมฆ), KeyboardInput(ล็อกการเดิน)
src/main/resources/assets/ascension_rings/textures/vfx/yy/   scripture, dragon_white/black, burst, light_pillar, bagua_ring, ripple, flare, ring_edge
src/main/resources/assets/ascension_rings/sounds/             yy_*.ogg (เสียงต้นฉบับ) + sounds.json
src/main/resources/assets/ascension_rings/textures/vfx/
    ring_0..3 (FireMage), vortex_0..1 + orb (BloodMage), magic_circle, lily
```

ปรับจังหวะ/ความแรง cutscene ได้ที่ `Timeline.java` และ `CameraEffects.java` (เป็น keyframe อ่านง่าย)
