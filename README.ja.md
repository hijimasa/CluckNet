# CluckNet

[English](README.md) | **日本語**

> Minecraft 上で [RFC 1149「鳥類キャリアによる IP データグラム送信規格」](https://datatracker.ietf.org/doc/html/rfc1149) を実装したもの。

ブロック間のパケットを **ニワトリ** が物理的に運びます。ニワトリは飛べず、障害物・水・モブで容易に止まり、プレイヤーの誤射で簡単に死にます &mdash; この性質がそのまま「IP 通信におけるパケットロスと遅延」のリアルなシミュレーションになります。

この Minecraft Forge Mod は、まず第一にジョークです。第二に、損失のあるネットワーク・前方誤り訂正・再送プロトコルが「光ファイバではなく物理世界の上で動いたら何が起きるか」を手で触って観察できるサンドボックスでもあります。

---

## 背景

[RFC 1149](https://datatracker.ietf.org/doc/html/rfc1149) (1990 年 4 月 1 日付) と後継の [RFC 2549](https://datatracker.ietf.org/doc/html/rfc2549) (1999 年 4 月 1 日付) は、鳥 (鳩) を使った IP データグラム伝送を規定しています。CluckNet はこのジョークを「実際に動かして見られる世界」に持ち込んだものです。

なぜインコやオウムではなくニワトリか?
- **ニワトリは飛べない。** 滑空止まりで、1.5 ブロックの段差で立ち往生し、水で流され、オオカミに食われます。最善努力配送 (best-effort delivery) を物理的に体現するのにこれ以上ふさわしい家禽はありません。
- 13 ブロックを歩かせるだけでスループットがボトルネックになり、パケットロスの主因にもなる &mdash; これは設計上の特徴です。

### この Mod が追加するもの

| 概念 | RFC 1149 用語 | CluckNet での実体 |
|---|---|---|
| 送信ホスト | 始点ゲートウェイ | **Packet Sender** ブロック (天面: 赤) |
| 受信ホスト | 終点ゲートウェイ | **Packet Receiver** ブロック (天面: 青) |
| データグラム | IP datagram | ニワトリが運ぶ `CompoundTag` ペイロード |
| 鳥類キャリア | Carrier | **Packet Chicken** エンティティ |
| MTU | 1500 byte | **16 byte** (分割挙動が見えるよう意図的に小さく) |
| ACK / NACK | TCP ACK | 欠損 chunk index の配列を運ぶ戻りニワトリ |
| 前方誤り訂正 | (RFC 1149 にはない) | メッセージあたり **XOR パリティ chunk 1 個** |

---

## 必要環境

- Minecraft **1.20.6**
- Minecraft Forge **50.1.x**
- **Java 21** (未インストールの場合は Gradle が自動取得)
- Linux の inotify 上限がそこそこ広いこと:
  ```
  /proc/sys/fs/inotify/max_user_instances >= 256
  /proc/sys/fs/inotify/max_user_watches   >= 262144
  ```
  起動時に `Couldn't watch config file` が出た場合は [トラブルシューティング](#トラブルシューティング)を参照。

---

## クイックスタート (開発用クライアント)

```bash
git clone <this-repo-url> CluckNet
cd CluckNet
./runClient.sh
```

初回実行は数分かかります &mdash; Forge MDK が Minecraft を取得・逆コンパイル・マッピング適用するためです。2 回目以降は速くなります。

タイトル画面が出たら: Singleplayer → Create New World → Creative、Flat preset で OK。

---

## ウォークスルー: メッセージを送る

### 1. ブロックを入手
Creative インベントリの **CluckNet** タブを開くと:
- **Packet Sender** (赤天面・木製の本体) &mdash; 送信端点
- **Packet Receiver** (青天面・木製の本体) &mdash; 受信端点

サバイバルでは両方ともクラフト可能:

```
Sender                Receiver
P R P                 P L P
P F P                 P E P
P P P                 P P P
```

- `P` = オークの板材 &nbsp; `R` = レッドストーンブロック &nbsp; `F` = 羽根
- `L` = ラピスラズリブロック &nbsp; `E` = エンダーアイ

### 2. お互い到達可能な位置に設置
それぞれの上 1 ブロック分の空間を空けた状態で、Sender と Receiver を地面に置きます。

### 3. リンクする
3 通りの方法があります。

**A. スニーク右クリックでペアリング (推奨)**

1. スニーク (Shift) しながら **Receiver** を右クリック &mdash; チャット: `Receiver -32, 64, 92 captured. Sneak-right-click a Sender to complete.`
2. スニークしたまま **Sender** を右クリック &mdash; チャット: `Linked Sender ... → Receiver ...`

**B. Sender GUI の座標欄に入力**

1. Sender を素手で (スニークなしで) 右クリック → GUI が開く
2. **Destination** 欄に受信機の座標を入力 (例: `-32 64 92` または `-32,64,92`)
3. ステータス行に `Linked → -32, 64, 92` と表示されればバインド完了

**C. コマンド (フォールバック)**

```
/clucknet link <sender_pos> <destination_pos>
```

### 4. メッセージを書いて送る
1. **本と羽根ペン (Writable Book)** を入手 &mdash; Creative の「Tools & Utilities」タブから、または `本 + イカ墨 + 羽根` でクラフト
2. 手に持って右クリックで開き、文章を書いて閉じる (**署名しない** &mdash; 署名済み本 = Written Book は保管用なので Sender は受け取りません)
3. Sender を右クリックして GUI を開く
4. 中央の入力スロットに本と羽根ペンを入れる
5. 本は消費され、データ chunk 数 + パリティ 1 個 分のニワトリが放出されます

### 5. ニワトリを見守る
- 各ニワトリは「宛先方位 + ガウス雑音」のバイアス付き乱歩で受信機に向かいます。
- 素手でニワトリを右クリックすると、運んでいるパケット情報をチャットで確認できます:
  ```
  Packet 8bc05e5f — chunk 1/3 (16 B) → -32,64,92
  Payload: "Hello CluckNet"
  ```
- ニワトリを殺すと、ロスした chunk のプレビューを記載した **紙アイテム** をドロップします。

### 6. 受信
- 再構築に成功すると:
  - チャット: `[CluckNet @ -32,64,92] received: "Hello CluckNet RFC..."` (アクア色)
  - **Written Book** が受信機の内部インベントリに追加されます (受信機を右クリックで開いて取り出し)
- ニワトリ 1 羽がロスしてもパリティが届けば、上と同じだが末尾に `(1 chunk recovered via parity)` (ゴールド色)
- 20 秒経っても再構築不能 → 受信機が **NACK ニワトリ** を送信機に戻し、送信機が欠損 chunk のみ再送
- 60 秒経っても揃わない: `[CluckNet @ ...] incomplete: M/N chunks (missing 0,2)` (赤色)

### 7. 統計コマンド
`/clucknet stats` を実行するとプロセス全体のパケットカウンタをチャットに出力します:
```
[CluckNet stats] sent: data=12 parity=4 retransmit=1  recv: chunks=11 msgs=3 parity-fix=1  loss: timeouts=0 nacks=1 chickens=2
```
(Forge 1.20.6 では Mojang の `LayeredDraw` API への移行に伴い HUD オーバーレイ用イベントが削除されたため、現状 Forge 側から HUD レイヤーを登録する手段がありません。同じカウンタをコマンドで露出する方式に退避しています。)

---

## プロトコル概要

```
[Sender Block]
   メッセージ (UTF-8 バイト列)
     ↓  16 byte の data chunk に分割、最後を 0 詰め
     ↓  全 data chunk を XOR → parity chunk
   N+1 羽の Packet Chicken を spawn → 受信機方向へ歩行
   60 秒間 SentMessageState を保持 (再送用)

[Packet Chicken]                       [Receiver Block]
   waypoint = 8 ブロック先 +              5 tick おきにスキャン
   ガウス雑音 (σ ≈ 30°)                   destination == 自分のニワトリを捕獲
   TTL = 3 分                            messageId 別バッファに再構築
   右クリック → ペイロード表示          完成 → Written Book を内部インベントリへ
   死亡 → ラベル付き紙をドロップ                       broadcast "received"
                                        20 秒滞留 → NACK ニワトリを送出
                                        60 秒未完 → broadcast "incomplete"
```

NACK のエンコード: ニワトリの `payload` は big-endian `int32` の配列で欠損 chunk index を詰めたもの。ニワトリの destination は元の送信機 BlockPos。

XOR パリティ復元: データ chunk がちょうど 1 個だけ欠損していて、パリティ chunk が届いていれば、受信側はパリティと残りの受信 data chunk (MTU バイトに 0 詰めしたもの) の XOR で欠損 chunk を再構築します。

---

## 配送セマンティクス (TCP 風の eager 出力)

受信機は標準的な TCP 流のセマンティクスに従います: **再構築できる材料が揃った時点で即座に配送します。** 全ての期待ニワトリの到着を待ったりはしません。

| 到着パターン | 受信機の振る舞い |
|---|---|
| N 個の data chunk 全部到達 | 即座に再構築・配送。あとから到着する parity chunk は **冗長として黙って破棄**。 |
| N − 1 個の data chunk + parity 到達 | XOR で欠損 data chunk を復元 → 即配送。あとから遅れて届く対応 data chunk は黙って破棄。 |
| 60 秒経っても足りない | incomplete を broadcast。messageId は "finalised" として記憶され、超遅延 chunk が来ても新しいメッセージとは誤認されない。 |

メッセージごとに送信時に新しい `UUID` が生成されます。受信機はファイナライズ後 60 秒間 `Map<UUID, finalisedTick>` を保持:

```java
if (finalizedMessages.containsKey(data.messageId())) continue; // 遅れて来た冗長 chunk → 破棄
```

キーはメッセージ別 UUID なので、**抑制は厳密にそのメッセージだけに作用** します &mdash; 次のメッセージは新しい UUID を持っており finalised エントリと衝突しないため、通常通り処理されます。クロスメッセージのブロックは起きません。

**なぜ「全ニワトリ揃うまで待つ」ではなく eager か?**
- パリティは純粋に冗長/復元用です。データが揃っていればパリティは何の新情報も運んでこないので、それを待たせるのは「無のための待ち時間」を増やすだけです。
- もしロスするのがパリティそれ自体だった場合、待っていたら 60 秒のタイムアウト一杯まで何も出力されません &mdash; 実際にはすでに復元可能だったのに。
- これは RFC 793 (TCP) の受信側挙動と一致し、規格準拠のあらゆるトランスポートが採る方針です: 揃った in-order データを即座に届け、冗長な後続は重複として破棄する。

トレードオフ: 視覚的には、**チャットメッセージや受信箱の Written Book が、全ニワトリが歩き終わる前に出てしまう** ことがよく起こります。遅れて到着したニワトリは冗長と認識されて静かに破棄されます。これは正常動作 &mdash; ネットワークが最大スループットで動いている証拠です。

---

## プロジェクト構成

```
src/main/java/com/hijimasa/clucknet/
├── CluckNet.java              エントリポイント、登録全般
├── block/                     Sender / Receiver ブロックと BlockEntity
├── entity/
│   ├── ModEntities.java       Packet Chicken の登録
│   ├── PacketChicken.java     バニラ Chicken を継承、chunk NBT 保持
│   └── goal/WanderTowardReceiverGoal.java   バイアス付き乱歩 AI
├── menu/                      AbstractContainerMenu + IForgeMenuType
├── client/                    GUI Screen、MenuScreens 登録
├── network/                   Forge SimpleChannel + UpdateSenderDestinationPacket
├── event/LinkingHandler.java  sneak-click リンク
├── command/CluckNetCommand.java  /clucknet send/link/fire/message/stats
├── stats/CluckNetStats.java   プロセス別カウンタ
└── item/                      BlockItem + Creative Tab

src/main/resources/
├── META-INF/mods.toml
├── data/clucknet/             レシピ、ルートテーブル
└── assets/clucknet/           blockstate、model、lang、texture (バニラ参照)
```

---

## コマンド

| コマンド | 効果 |
|---|---|
| `/clucknet send <pos>` | プレイヤーの足元にペイロードなしのニワトリを spawn し `<pos>` へ向ける (Phase 1 の動作確認用)。 |
| `/clucknet link <sender_pos> <destination_pos>` | Sender ブロックに宛先をバインド。 |
| `/clucknet fire <sender_pos>` | Sender ブロックを手動発射。 |
| `/clucknet message <sender_pos> <text...>` | Sender の保留メッセージを設定 (次のレッドストーン入力 / 手動発射で送信される)。 |
| `/clucknet stats` | パケット統計カウンタをチャットに出力 (削除された HUD オーバーレイの代替)。 |

すべてのコマンドはゲーム内 `/` コンソールから使えるので、GUI とコマンドを混在させても問題ありません。

---

## トラブルシューティング

### 起動時に `Couldn't watch config file`

**CluckNet のバグではありません。** Forge は JVM `WatchService` を使っており、Linux では裏で `inotify` を消費します。VSCode / IntelliJ / Cursor を複数インスタンス動かしているような開発機ではデフォルトの `fs.inotify.max_user_instances = 128` が Forge の起動前に枯渇します。

一時的な対処:
```bash
sudo sysctl -w fs.inotify.max_user_instances=512
sudo sysctl -w fs.inotify.max_user_watches=524288
```

恒久対処:
```bash
sudo tee /etc/sysctl.d/99-inotify-limits.conf <<'EOF'
fs.inotify.max_user_instances = 512
fs.inotify.max_user_watches   = 524288
EOF
sudo sysctl --system
```

### 起動時に `Module jopt.simple not found`

Forge 1.20.6 + Java 21 では `jopt-simple` を `5.0.4` に固定する必要があります (それ以外のバージョンだと Automatic-Module-Name が `cpw.mods.modlauncher` の期待と一致しません)。既に [`build.gradle`](build.gradle) で固定済み。フォーク時はこの制約を残してください。

### ニワトリが詰まって永遠に届かない

これは仕様であってバグではありません。実用的な解決:
- 送受信機間の道を平坦に整える (ニワトリは 1 ブロックは登れるが 2 ブロックは無理)
- 経路に水流を残さない
- 退屈してニワトリを弓で撃たない

到達保証が必要であればパリティ chunk 数を増やす (TODO &mdash; 現状 1 固定)、または NACK + 再送サイクルを利用してください。

---

## Phase 一覧

| Phase | 機能 | 状態 |
|---|---|---|
| 0 | `minecraft_ros2` をテンプレに Forge プロジェクト雛形 | ✅ |
| 1 | `PacketChicken` Entity + バイアス付き乱歩 + `/clucknet send` | ✅ |
| 2 | Sender ブロック、レッドストーントリガ、`/clucknet link/fire` | ✅ |
| 3 | Receiver ブロック、ペイロード分割、メッセージ再構築 | ✅ |
| 4a | Sender GUI: 本と羽根ペン入力 + 宛先 EditBox | ✅ |
| 4b | Receiver GUI: 受信完了 Written Book の出力インベントリ | ✅ |
| 4c | スニーク右クリックによるリンクトークン方式のペアリング | ✅ |
| 5a | XOR パリティ chunk による単一ロス復元 | ✅ |
| 5b | NACK 戻りニワトリ、欠損 chunk の再送 | ✅ |
| 6a | 両ブロックのクラフトレシピ | ✅ |
| 6b | 統計カウンタ (`/clucknet stats` — HUD は保留、上記注を参照) | ✅ |
| 6c | 死亡時ドロップ (ロスした chunk がラベル付き紙として残る) | ✅ |
| 7 (将来) | ROS 2 ブリッジ (任意、[`minecraft_ros2`](https://github.com/minecraft-ros2/minecraft_ros2) と対応) | — |

設計メモ: [`../IPoAC_Minecraft_Mod_方針資料.md`](../IPoAC_Minecraft_Mod_%E6%96%B9%E9%87%9D%E8%B3%87%E6%96%99.md)

---

## ライセンス

MIT. [LICENSE](LICENSE) を参照。

[`minecraft_ros2`](https://github.com/minecraft-ros2/minecraft_ros2) (Kazusa Hashimoto) を Forge 1.20.6 / Java 21 のリファレンス構造として使用しています。ROS 2 連携コードは一切含まれていません。
