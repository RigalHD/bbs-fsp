# BBS FSP — инструкция по реализации Canvas/Paint Clip

## 1. Назначение документа

Этот документ является самодостаточным техническим заданием для реализации отдельного Fabric-аддона `bbs-fsp` к BBS FS. Аддон добавляет новый клип таймлайна — `CanvasClip` (`Картина`) — для покадрового рисования пиксель-арта непосредственно поверх предпросмотра и экспортируемой сцены.

Реализацию вести в соседней папке:

```text
/Users/rigalhd/Documents/Codex/BBS/bbs-fsp
```

Исходники BBS FS находятся здесь:

```text
/Users/rigalhd/Documents/Codex/BBS/bbs-fs
```

## 2. Целевые версии

Аддон собирается в четыре отдельных JAR (см. `targets/*.properties` и `scripts/build-all.sh`):

| Minecraft | Java | Fabric API | BBS FS | Коммит BBS FS |
| --- | --- | --- | --- | --- |
| 1.20.4 | 17 | 0.91.3+1.20.4 | 2.6 / 2.6.1 | `5b913c52b` (2.6), `fb6795eeb` (2.6.1) |
| 1.20.4 | 17 | 0.91.3+1.20.4 | 2.7 | `13bd62dab` |
| 1.21.11 | 21 | 0.141.4+1.21.11 | 2.6 / 2.6.1 | `f66cdcb7b` (2.6), `8808a3b85` (2.6.1) |
| 1.21.11 | 21 | 0.141.4+1.21.11 | 2.7 | `9fb7f936a` |

JAR серии 2.6 компилируется против 2.6.1 и дополнительно проверяется компиляцией против 2.6.0. Ветка `dev` BBS FS уже несёт версию 2.8 и не поддерживается, пока не выйдет релиз.

BBS FS публикует версию как `<версия>-<minecraft>` (например `2.6-1.20.4`). По семантическому версионированию это пре-релиз, он меньше `2.6`, поэтому диапазон зависимости обязан заканчиваться дефисом: `>=2.6- <2.7-`. Диапазон `>=2.6 <2.7` отвергает BBS 2.6 и принимает 2.7.

Упоминание BBS FS `1.5.2` в исходной идее считать ошибкой, пока заказчик явно не предоставит исходники этой версии. Не заявлять совместимость с BBS FS 1.5.2 или Minecraft 1.20.1 без отдельной проверки.

### 2.1 Исправления к прежней редакции плана

Прежняя редакция содержала ошибки, найденные при сверке с исходниками BBS FS:

1. Регистрация клипа из обычного Fabric `main`-entrypoint «после инициализации BBS» не гарантирована: Fabric Loader не упорядочивает entrypoint'ы разных модов. Если аддон окажется раньше BBS, `getFactoryCameraClips()` вернёт `null`. Правильно — подписка на `RegisterCameraClipsEvent` (BBS публикует его сам, создав фабрику) и `RegisterClipPanelsEvent` для панели (§7.3).
2. Диапазон `"bbs": ">=2.6 <2.7"` в `fabric.mod.json` не пропускал BBS 2.6 (см. выше).
3. Смещение холста «в пикселях итогового viewport/output» нельзя выдержать одинаково во всех путях рендера: превью рисуется в размере панели, мир в редакторе — в размере экспорта, передний план — в размере окна. Смещение задаётся в клетках холста (§8.1).
4. По умолчанию кадр содержит не один слой, а три — по одному на каждую категорию рендера (§8.4).
5. `RenderStateGuard` и восстановление GL-состояния применимы только к 1.20.4. На 1.21.11 нет глобального состояния шейдера: глубина, смешивание и отсечение задаются собственными `RenderPipeline`, а 2D-интерфейс отложенный (§13.3).
6. Конфигурация миксинов с `"required": false` и `defaultRequire: 0` молча отключала рисование при несовпадении цели. Теперь `defaultRequire: 1`, а цели проверяются по байткоду каждой поддерживаемой сборки BBS.
7. Кэш текстур никогда не освобождал текстуры слоёв, заменённых после undo/redo. Теперь неиспользуемые 10 секунд текстуры удаляются.

## 3. Обязательные ограничения

1. Не изменять исходники `bbs-fs`.
2. Реализовать функциональность отдельным JAR-модом в `bbs-fsp`.
3. Разрешены mixin-инъекции из `bbs-fsp` в BBS FS и Minecraft.
4. Не копировать целиком редактор текстур BBS и не создавать форк BBS FS.
5. Не хранить клиентские или OpenGL-объекты в сериализуемой модели клипа.
6. Common-код должен безопасно загружаться на dedicated server.
7. Не создавать git commit без прямого запроса пользователя.
8. Если `bbs-fsp` является git-репозиторием, работать в ветке `feature/canvas-clip`, а не в `master`.
9. Не читать файлы `.env`, если в их имени нет слова `EXAMPLE`.
10. Не оставлять в коде временные комментарии, TODO, закомментированный код и отладочный вывод.

## 4. Главный архитектурный вывод

Весь функционал реализуем отдельным аддоном, однако одних публичных событий BBS FS недостаточно.

Через публичные API выполняются:

- регистрация типа клипа;
- сериализация в `Film`;
- регистрация UI-панели;
- регистрация ресурсов и переводов.

Mixin-инъекции необходимы для:

- обработки рисования непосредственно во viewport;
- точных стадий рендера «за структурами» и «за энтити»;
- foreground-рендера в правильный момент относительно экспорта BBS;
- получения активного фильма в отдельных путях воспроизведения и F6-экспорта.

Если mixin-инъекции запрещены, полный набор требований реализовать нельзя.

## 5. Обязательные точки изучения в BBS FS

Перед написанием соответствующей подсистемы открыть и изучить актуальный код этих классов:

```text
src/main/java/mchorse/bbs_mod/BBSMod.java
src/client/java/mchorse/bbs_mod/BBSModClient.java
src/main/java/mchorse/bbs_mod/utils/factory/MapFactory.java
src/main/java/mchorse/bbs_mod/utils/factory/IFactory.java
src/main/java/mchorse/bbs_mod/utils/clips/Clip.java
src/main/java/mchorse/bbs_mod/utils/clips/Clips.java
src/main/java/mchorse/bbs_mod/film/Film.java
src/main/java/mchorse/bbs_mod/film/Films.java
src/main/java/mchorse/bbs_mod/film/BaseFilmController.java
src/client/java/mchorse/bbs_mod/ui/film/UIClips.java
src/client/java/mchorse/bbs_mod/ui/film/UIClipsPanel.java
src/client/java/mchorse/bbs_mod/ui/film/UIFilmPreview.java
src/client/java/mchorse/bbs_mod/ui/film/clips/UIClip.java
src/client/java/mchorse/bbs_mod/ui/film/clips/UIPathClip.java
src/client/java/mchorse/bbs_mod/ui/dashboard/textures/data/Document.java
src/client/java/mchorse/bbs_mod/ui/dashboard/textures/data/TextureLayer.java
src/client/java/mchorse/bbs_mod/ui/dashboard/textures/UIPixelsEditor.java
src/client/java/mchorse/bbs_mod/ui/dashboard/textures/UITexturePainter.java
src/main/java/mchorse/bbs_mod/utils/resources/Pixels.java
```

Не угадывать имена методов и сигнатуры mixin targets. Всегда сверять их с текущим исходным кодом и скомпилированными mappings.

## 6. Структура проекта

Использовать приблизительно следующую структуру:

```text
bbs-fsp/
├── build.gradle
├── settings.gradle
├── gradle.properties
└── src/
    ├── main/
    │   ├── java/.../BbsFspMod.java
    │   ├── java/.../BbsFspAddon.java
    │   ├── java/.../canvas/CanvasClip.java
    │   ├── java/.../canvas/CanvasFrameData.java
    │   ├── java/.../canvas/CanvasLayerData.java
    │   ├── java/.../canvas/CanvasRenderBand.java
    │   ├── java/.../canvas/ValueCanvasFrames.java
    │   └── resources/
    │       ├── fabric.mod.json
    │       └── bbs_fsp.mixins.json
    └── client/
        ├── java/.../BbsFspClient.java
        ├── java/.../BbsFspClientAddon.java
        ├── java/.../ui/UICanvasClip.java
        ├── java/.../ui/UICanvasFrameStrip.java
        ├── java/.../ui/CanvasViewportController.java
        ├── java/.../ui/UICanvasLayersPanel.java
        ├── java/.../render/CanvasRenderer.java
        ├── java/.../render/CanvasTextureCache.java
        ├── java/.../render/CanvasPlaybackResolver.java
        ├── java/.../render/RenderStateGuard.java
        ├── java/.../tools/*.java
        ├── java/.../mixin/UIFilmPreviewMixin.java
        ├── java/.../mixin/WorldRendererMixin.java
        ├── java/.../mixin/GameRendererMixin.java
        ├── java/.../mixin/FilmsAccessor.java
        └── resources/
            ├── bbs_fsp.client.mixins.json
            └── assets/bbs_fsp/
```

Допускается изменить пакеты и разбиение классов, но нельзя смешивать common и client-only код.

Код, зависящий от версии Minecraft (кэш текстур, отрисовка слоёв в интерфейсе, проходы рендера в мире и перед экраном, чтение пикселя экрана), лежит в `src/mc1204/client/java` и `src/mc12111/client/java` с одинаковыми именами классов; Gradle подключает нужный каталог по свойству `platform` выбранной цели.

## 7. Регистрация аддона и клипа

### 7.1 Тип клипа

Создать:

```java
public final class CanvasClip extends Clip
```

Не наследовать `CanvasClip` от `CameraClip`. `CameraClipContext` считает активные камерные клипы, и пустой наследник способен изменить семантику существующих трансформаций камеры.

Клип должен находиться в camera timeline BBS, но отдельно обрабатываться Canvas-рендерером.

### 7.2 Идентификатор

Использовать namespace аддона:

```text
bbs_fsp:canvas
```

Не регистрировать тип в namespace `bbs`.

### 7.3 Порядок инициализации

BBS загружает `bbs-addon` раньше, чем создаёт фабрику камерных клипов, а затем публикует `RegisterCameraClipsEvent`. Fabric Loader не гарантирует порядок `main`-entrypoint'ов разных модов, поэтому вызывать `BBSMod.getFactoryCameraClips()` из своего `main`-entrypoint нельзя.

Использовать:

- `bbs-addon` (обе стороны): `RegisterSourcePacksEvent` и `RegisterCameraClipsEvent`;
- `bbs-client-addon`: `RegisterL10nEvent` и `RegisterClipPanelsEvent` для `UICanvasClip`;
- обычный Fabric `client` entrypoint только для событий рендера мира и отключения от сервера.

Регистрация клипа должна быть эквивалентна:

```java
@Subscribe
public void registerCameraClips(RegisterCameraClipsEvent event)
{
    event.factory.register(new Link("bbs_fsp", "canvas"), CanvasClip.class,
        new ClipFactoryData(canvasIcon, 0xF28C28));
}
```

У класса должен быть публичный конструктор без аргументов, так как фабрика создаёт экземпляры рефлексивно.

## 8. Модель данных

### 8.1 CanvasClip

Минимальные поля:

```text
width = 120
height = 68
offsetX = 0
offsetY = 0
rotationDegrees = 0
gridOpacity = 0.5
frames = ValueCanvasFrames
```

Смысл `offsetX` и `offsetY`: смещение холста от центра кадра в клетках холста (дробные значения допустимы). В любом пути рендера оно переводится в единицы области рисования как `offset * ширина_области / ширина_холста`, поэтому сетка редактора, слои в мире и передний план совпадают при любом разрешении.

Разделять:

- opacity линий редакторской сетки;
- opacity слоя;
- alpha отдельных пикселей;
- opacity активного инструмента.

### 8.2 Кадр

```text
CanvasFrameData
  tick: int
  layers: ordered list<CanvasLayerData>
```

Инварианты:

- кадры отсортированы по `tick`;
- тики уникальны;
- допустимый диапазон `0 <= tick < clip.duration`;
- максимум один кадр на тик;
- между кадрами действует step/hold: используется последний кадр с `frame.tick <= localTick`;
- интерполяции изображения нет.

### 8.3 Начальные кадры

При длительности 30 тиков создать 15 кадров:

```text
0, 2, 4, ..., 28
```

BBS присваивает длительность новому клипу после создания экземпляра. Не создавать окончательный набор кадров в конструкторе `CanvasClip`.

Добавить идемпотентный метод вида:

```text
ensureDefaultFramesAfterDurationAssigned()
```

Вызывать его после того, как UI BBS назначил клипу длительность, либо при первом открытии панели нового пустого клипа. Не перезаписывать уже загруженные кадры.

Если требование «всегда 30 тиков» строгое, добавить точечную инъекцию после создания Canvas-клипа. Иначе можно использовать глобальную default duration BBS и создавать `duration / 2` кадров.

### 8.4 Слой

```text
CanvasLayerData
  id: stable string/UUID
  name: string
  renderBand: CanvasRenderBand
  order: int
  visible: boolean
  opacity: float
  pixels: int[] ARGB
```

Категории:

```java
BEHIND_STRUCTURES,
BEHIND_ENTITIES,
FOREGROUND
```

По умолчанию кадр содержит три видимых слоя с opacity 1.0: `FOREGROUND`, `BEHIND_ENTITIES` и `BEHIND_STRUCTURES`.

Внутри одной категории слои сортируются по `order`; больший `order` рисуется выше.

Не путать `Clip.layer`, то есть дорожку BBS, с внутренним слоем рисунка.

### 8.5 Сериализация

Хранить для каждого кадра список слоёв и `int[]` ARGB длиной строго `width * height`.

При чтении валидировать:

- диапазон width/height;
- отсутствие переполнения `width * height`;
- точную длину массива;
- максимальное количество кадров и слоёв;
- уникальность тиков;
- корректность enum;
- конечность чисел transform/opacity;
- диапазон opacity 0..1.

`Document` и `TextureLayer` из редактора BBS нельзя сохранять непосредственно: они клиентские и содержат GPU-ресурсы.

### 8.6 Изменение размера

При resize:

- показать явное подтверждение;
- изменить все кадры и слои;
- использовать nearest-neighbor;
- выполнить всё одной undo-операцией;
- не менять aspect ratio молча.

### 8.7 Изменение длительности

При увеличении длительности существующие кадры не менять.

При уменьшении:

- определить кадры за новым концом;
- запросить подтверждение удаления;
- удалить их одной undo-операцией.

### 8.8 Разрезание клипа

Переопределить семантику `breakDown()`:

- левый клип получает кадры до точки разреза;
- правый клип получает на локальном tick 0 копию изображения, видимого в момент разреза;
- более поздние кадры сдвигаются на `-splitTick`;
- не создавать два кадра на tick 0;
- данные слоёв копировать глубоко.

## 9. UI редактора

Создать `UICanvasClip extends UIClip<CanvasClip>` и зарегистрировать его:

```java
UIClip.register(CanvasClip.class, UICanvasClip::new);
```

Панель должна содержать:

1. Width и height.
2. Offset X/Y.
3. Rotation.
4. Grid opacity.
5. Мини-таймлайн кадров.
6. Список слоёв.
7. Палитру и alpha активного цвета.
8. Инструменты и их параметры.

### 9.1 Мини-таймлайн

Создать отдельный `UICanvasFrameStrip`. Не использовать обычный интерполируемый `KeyframeChannel` для хранения изображений.

Поддержать:

- отображение всех frame ticks;
- переход к кадру;
- предыдущий/следующий кадр;
- добавление;
- удаление;
- дублирование;
- создание пустого кадра;
- drag по шкале времени;
- запрет совпадающих тиков;
- автоматическое перемещение глобального film cursor при выборе маркера.

Добавление кадра по умолчанию дублирует текущее видимое изображение. Отдельное действие создаёт пустой кадр.

### 9.2 Панель слоёв

Поддержать:

- добавить слой;
- удалить слой;
- переименовать;
- изменить порядок;
- изменить render band;
- visibility;
- opacity;
- выбор активного слоя.

Удаление непустого слоя должно запрашивать подтверждение.

## 10. Рисование во viewport

Режим рисования включается автоматически, когда выбран `CanvasClip`.

Сетка и editor overlays отображаются только при выбранном Canvas-клипе.

Для позиции мыши выполнить преобразование:

```text
screen coordinates
→ viewport-local coordinates
→ coordinates relative to viewport center
→ inverse offset
→ inverse rotation
→ normalized canvas coordinates
→ integer cell X/Y
```

Обязательно протестировать преобразование для вращений 0°, 45°, 90°, отрицательных углов и смещённого холста.

Добавить hover-подсветку текущей ячейки: контур отпечатка текущей кисти (для кистей больше 8 клеток — только его край).

Сетка серая (`0x8C8C8C`), её непрозрачность задаётся параметром клипа.

Mixin к `UIFilmPreview` должен перехватывать mouse click/drag/release только если:

- выбран `CanvasClip`;
- координата находится внутри трансформированного холста;
- активный инструмент действительно обработал событие.

В остальных случаях не ломать orbit камеры, выбор объектов, scrubbing и стандартные shortcut BBS.

Grid, hover, selection и ghost preview не должны попадать в экспорт.

## 11. Инструменты

Использовать общий контракт вида:

```java
interface CanvasTool
{
    void begin(...);
    void update(...);
    void finish(...);
    void cancel(...);
    void renderPreview(...);
}
```

### 11.1 Pencil

- жёсткая квадратная кисть;
- размер 1×1 по умолчанию;
- регулируемый размер;
- поддержка RGBA.

### 11.2 Brush

- square и circle;
- размер 1..N;
- hardness/softness;
- tool opacity;
- непрерывный stroke без дырок между mouse events.

Интерполировать точки движения мыши, чтобы быстрый drag не создавал разрывы.

### 11.3 Eraser

- стирать в прозрачность;
- учитывать opacity инструмента;
- поддерживать те же shape/size, что и brush.

### 11.4 Fill

- четырёхсвязная flood fill;
- сравнивать полный RGBA;
- предусмотреть tolerance только как дополнительную настройку;
- не зависать на больших областях;
- не выполнять операцию, если target и replacement совпадают.

### 11.5 Eyedropper

Alt+клик по холсту берёт итоговый цвет композиции видимых слоёв в выбранной клетке, а не только цвет активного слоя. Alpha также копируется. На пустой клетке берётся цвет экрана.

Кнопка пипетки работает на весь экран: следующий левый клик в любом меню BBS берёт цвет пикселя под курсором (превью фильма, холст, панели, образцы цветов), правый клик или Escape отменяют. Пиксель читается после того, как меню отрисовало кадр: на 1.20.4 — `glReadPixels` из основного буфера, на 1.21.11 — через `ScreenPixelProbe` BBS после композиции отложенного интерфейса (значение на кадр старше). Пока пипетка активна, сетка и контур кисти не рисуются, чтобы не попадать в выборку. Цвет экрана непрозрачен, поэтому alpha краски сохраняется.

### 11.5.1 Редактор кисти

Паттерны: карандаш, маркер, градиент, дизеринг, распылитель. Формы: квадрат, круг, ромб, горизонтальная и вертикальная линия. Размер 1–64. Мягкость (маркер, градиент, распылитель), непрозрачность кисти (все, кроме карандаша), плотность (дизеринг, распылитель), длина градиента. Ластик — режим любой кисти. Каждый паттерн хранит свои настройки; настройки и оба цвета сохраняются в `bbs_fsp_brushes.json` в папке настроек BBS.

Мазок хранит для каждого пикселя максимальное покрытие за мазок и пересобирает пиксель из состояния слоя до начала мазка. Поэтому полупрозрачный маркер не темнеет на пересечениях соседних отпечатков, а мягкий край не съедает середину того же мазка. Градиент меняет цвет от основного ко второму по длине пройденного пути и обратно. Дизеринг использует матрицу Байера 4×4, привязанную к клеткам холста. Распылитель детерминирован зерном мазка.

### 11.6 Line

Использовать алгоритм Брезенхема. До отпускания кнопки показывать preview, не изменяя слой окончательно.

### 11.7 Rectangle

Поддержать:

- outline;
- filled;
- square modifier.

### 11.8 Ellipse

Использовать midpoint ellipse либо эквивалентный целочисленный алгоритм. Поддержать outline, filled и circle modifier.

### 11.9 Gradient

Поддержать:

- horizontal;
- vertical;
- arbitrary linear/diagonal;
- radial;
- два и более color stops;
- применение к выделению или всему активному слою.

Сортировать stops по позиции 0..1. Интерполировать premultiplied RGBA, затем корректно преобразовывать обратно, чтобы избежать тёмных краёв у прозрачных цветов.

### 11.10 Blur

Использовать separable Gaussian blur:

1. горизонтальный проход;
2. вертикальный проход.

Параметры:

- radius;
- sigma;
- выделение или весь слой.

Обрабатывать цвета в premultiplied alpha. Для больших изображений вычислять по immutable snapshot вне render thread, но применять результат на render thread и только если revision слоя с момента запуска не изменился.

## 12. Undo и redo

Каждое логическое действие должно создавать ровно одну undo-запись:

- stroke;
- shape;
- gradient;
- blur;
- fill;
- добавление/удаление/перестановка слоя;
- resize;
- добавление/удаление/перемещение кадра;
- изменение длительности с удалением кадров.

Создать `ValueCanvasFrames extends BaseValue` с глубоким сериализуемым snapshot.

Не включать в snapshot:

- OpenGL texture IDs;
- client texture wrappers;
- hover state;
- selection preview;
- незавершённый stroke;
- временные render buffers.

## 13. Рендеринг

### 13.1 Общие правила

- Использовать nearest filtering.
- Не загружать текстуру заново каждый tick.
- Каждому слою/кадру присвоить revision.
- Обновлять GPU texture только при изменении revision.
- Освобождать texture при удалении клипа, закрытии фильма, disconnect и resource reload.
- Не выполнять постоянные аллокации массивов в render loop.
- Не хранить GPU-кэш в сериализуемом `CanvasClip`.

При 1920×1080 холст 120×68 должен масштабироваться приблизительно до экранных блоков 16×16 пикселей.

Порядок transform:

```text
viewport center
→ offset X/Y
→ rotation around canvas center
→ scale to output rectangle
```

### 13.2 Render bands

#### BEHIND_STRUCTURES

Рисовать после неба/фона, но перед solid terrain/chunks.

Результат:

- мир и структуры перекрывают картину;
- entities также перекрывают картину.

Depth writes выключить. Смешать Canvas с уже нарисованным небом/фоном.

#### BEHIND_ENTITIES

Рисовать после terrain и block entities, но до vanilla entities и BBS replay forms.

Результат:

- структуры находятся позади картины;
- entities и BBS forms находятся впереди картины.

Проверено в игре на обеих версиях: полупрозрачная геометрия (вода, витражи, облака) и рука игрока рисуются позже обеих мировых категорий и ложатся поверх них. Картина «за сущностями», видимая сквозь воду, подкрашивается водой. Это следует из порядка рендера Minecraft; если нужно иначе, категорию придётся переносить в другую точку мирового прохода.

#### FOREGROUND

Рисовать после мира, entities и BBS in-world rendering, но до копирования финального framebuffer для экспорта/preview.

Foreground означает «поверх экспортируемой сцены», но не поверх панелей редактора.

Если понадобится буквальный слой поверх Minecraft HUD, реализовывать его как отдельный `HUD` render band только после отдельного решения заказчика. Не смешивать его с текущим foreground.

### 13.3 Восстановление GL-состояния

Относится к 1.20.4. На 1.21.11 слои рисуются через собственные `RenderPipeline` (`position_tex_color`, смешивание `TRANSLUCENT`, без записи глубины; для `BEHIND_STRUCTURES` — `LEQUAL` на дальней плоскости, для остальных — без теста глубины) под ортографической проекцией в пикселях буфера, в который идёт кадр; восстанавливаются только проекция и model-view стек.

После каждого прохода на 1.20.4 восстановить:

- framebuffer;
- shader/program;
- active texture unit;
- texture binding;
- blend enable и blend function;
- depth test;
- depth mask;
- culling;
- viewport;
- projection/model-view matrices.

Реализовать единый `RenderStateGuard`, чтобы все render paths использовали одинаковую очистку состояния.

## 14. Editor preview и экспорт

В редакторе финальные scene layers уже должны находиться в framebuffer BBS. Поверх `UIFilmPreview` дополнительно рисовать только редакторские элементы:

- сетку;
- hover cell;
- selection;
- tool preview.

Не рисовать foreground Canvas повторно поверх preview, иначе изображение станет визуально плотнее из-за двойного alpha blending.

Проверить все пути:

- обычный editor preview;
- проигрывание Camera Work;
- panel export;
- F6/world export;
- pause;
- scrubbing;
- loop;
- transition;
- motion blur samples.

Нельзя получать состояние только из `PlayCameraController`: F6/world export способен проигрывать фильм без него.

Создать `CanvasPlaybackResolver`, который умеет получать:

- активный `Film`;
- текущий tick и partial tick;
- transition;
- текущие Canvas-клипы;
- режим editor/export/playback.

Если публичного getter недостаточно, добавить минимальный accessor-mixin к `Films` или hook в его render path. Не раскрывать больше внутреннего состояния, чем требуется.

## 15. Перекрывающиеся клипы

Порядок композиции должен быть детерминированным.

Использовать следующий порядок сортировки:

1. render band;
2. `Clip.layer` таймлайна BBS;
3. начало клипа;
4. стабильный порядок клипов в списке;
5. порядок внутренних Canvas-слоёв.

Документировать в UI, что более высокий timeline layer отображается выше в пределах одного render band.

## 16. Совместимость с Iris и IRLights

IRLights имеет mod ID:

```text
irlite
```

Прямая обязательная зависимость не нужна. При необходимости использовать:

```java
FabricLoader.getInstance().isModLoaded("irlite")
```

Обязательные правила:

- не рисовать Canvas во время Iris shadow pass;
- не изменять shaderpack;
- не использовать SSBO binding 7;
- не менять состояние IRLights;
- Canvas остаётся screen-space/unlit;
- полностью восстанавливать GL-state;
- избегать хрупких ordinal-инъекций;
- для необязательных compatibility hooks использовать безопасные injector constraints и проверять фактическое применение mixin на целевой версии.

Совместимость считать подтверждённой только после теста с реальным IRLights JAR на Minecraft 1.20.4 и BBS FS 2.6.

## 17. Иконка и цвет

Canvas-клип должен быть оранжевым:

```text
0xF28C28
```

Создать собственную 16×16 иконку кисти, палитры или холста в стиле существующих BBS icons.

Не переиспользовать иконку другого клипа как финальный вариант. Допускается временно использовать существующую `Icons.BRUSH` только на раннем этапе разработки.

Зарегистрировать source pack аддона через событие BBS `RegisterSourcePacksEvent`.

## 18. Локализация

Ключ отображаемого имени клипа:

```text
bbs.ui.camera.clips.bbs_fsp:canvas
```

Добавить полный набор строк редактора для всех 19 языков BBS:

```text
ar_ar
zh_cn
zh_tw
de_de
en_us
es_es
fr_fr
hu_hu
id_id
ko_kr
pl_pl
pt_br
pt_pt
ru_ru
th_th
tr_tr
uk_ua
vi_vn
ur_pk
```

Не ограничиваться английским fallback: условие проекта требует реальных переводов.

Добавить автоматическую проверку:

- каждый JSON парсится;
- набор ключей совпадает с `en_us`;
- placeholders совпадают;
- нет пустых обязательных значений.

## 19. Производительность и безопасность ресурсов

Ориентир для одного слоя одного кадра 120×68:

```text
120 * 68 * 4 = 32 640 bytes
```

15 кадров занимают примерно 490 КБ несжатых pixel data на слой.

Требования:

- сжатая сериализация через существующее хранилище BBS;
- отсутствие texture upload при неизменившемся кадре;
- отсутствие нового `int[]` каждый render call;
- LRU или lifecycle-aware GPU cache;
- явное удаление native/GPU ресурсов;
- пределы resolution, frame count и layer count при загрузке;
- async blur не должен применять устаревший результат;
- drawing operations должны работать только на client thread, если затрагивают UI/GPU.

## 20. Этапы реализации

Выполнять строго по порядку. После каждого этапа проект должен собираться.

### Этап 1. Каркас

- создать standalone Fabric-проект;
- подключить точный BBS FS 2.6;
- настроить common/client source sets;
- добавить `fabric.mod.json` и mixin configs;
- проверить запуск клиента и dedicated server.

### Этап 2. Данные

- реализовать `CanvasClip`;
- реализовать frames/layers/render bands;
- реализовать сериализацию и глубокое копирование;
- написать unit tests данных.

### Этап 3. Регистрация

- зарегистрировать тип клипа;
- добавить временную иконку и английское имя;
- убедиться, что клип создаётся, сохраняется и загружается.

### Этап 4. Кадры

- реализовать step/hold sampling;
- создать начальные кадры;
- реализовать add/delete/duplicate/move;
- реализовать duration changes и split.

### Этап 5. Foreground renderer

- реализовать texture cache;
- нарисовать активный кадр в foreground;
- проверить preview и экспорт;
- устранить двойной рендер.

### Этап 6. Viewport editing

- создать UI-панель;
- реализовать координатное преобразование;
- добавить сетку и hover;
- корректно перехватывать mouse input.

### Этап 7. Базовые инструменты

- pencil;
- brush;
- eraser;
- fill;
- eyedropper;
- один undo item на действие.

### Этап 8. Слои

- layer panel;
- order/visibility/opacity;
- active layer;
- композиция RGBA;
- render band selector.

### Этап 9. Фигуры

- line;
- rectangle;
- ellipse;
- interactive preview;
- outline/filled modes.

### Этап 10. Gradient и blur

- premultiplied-alpha gradient;
- multi-stop colors;
- radial mode;
- separable Gaussian blur;
- async calculation с revision check.

### Этап 11. Глубина

- реализовать `BEHIND_ENTITIES`;
- реализовать `BEHIND_STRUCTURES`;
- проверить порядок vanilla entities, BBS forms, blocks и block entities.

### Этап 12. Все playback/export paths

- editor;
- Camera Work;
- F6/world export;
- panel export;
- transition;
- motion blur;
- overlapping Canvas clips.

### Этап 13. Ресурсы и переводы

- создать финальную иконку;
- заполнить все 19 языков;
- добавить key-parity test.

### Этап 14. IRLights и стабилизация

- выполнить тестовую матрицу Iris/IRLights;
- проверить shadow pass guard;
- проверить GL state leaks;
- выполнить memory/GPU stress tests.

## 21. Тесты

### 21.1 Unit tests

Обязательно покрыть:

- serialize/deserialize без потери ARGB;
- alpha 0, частичную alpha и alpha 255;
- повреждённые и слишком большие pixel arrays;
- default 30 ticks → 15 frames;
- запрет двух кадров на одном тике;
- step/hold sampling;
- shrink/expand duration;
- copy и split;
- layer order/category/visibility/opacity;
- inverse transform и rotated hit testing;
- Bresenham;
- ellipse;
- flood fill;
- gradient golden arrays;
- blur golden arrays.

### 21.2 Integration tests

Проверить:

- клип отображается оранжевым и с правильной иконкой;
- Canvas UI открывается только для CanvasClip;
- сетка видна только при выбранном CanvasClip;
- drawing меняет правильный кадр и слой;
- undo/redo восстанавливает данные точно;
- save/reload фильма сохраняет все данные;
- dedicated server загружает фильм без client-only class errors;
- кадры переключаются точно на тиках 0, 1, 2, 28 и 29;
- loop не сдвигает анимацию;
- foreground попадает в каждый экспорт;
- сетка и hover не попадают в экспорт;
- wall/entity/BBS form корректно перекрывают разные render bands;
- overlapping Canvas clips имеют стабильный порядок;
- прозрачные края не имеют чёрных ореолов;
- удалённые клипы освобождают GPU textures;
- 100 циклов открытия/закрытия фильма не дают постоянного роста памяти;
- все 19 locale files проходят проверку.

### 21.3 IRLights matrix

Проверить комбинации:

```text
без Iris и без IRLights
Iris без IRLights
Iris + IRLights
```

Для каждой комбинации проверить:

- foreground;
- behind entities;
- behind structures;
- editor preview;
- F6 export;
- panel export;
- shadow pass;
- pause/scrubbing;
- motion blur;
- resize окна и output resolution.

## 22. Критерии готовности

Задача считается выполненной только если одновременно верно следующее:

1. В `bbs-fs` нет отслеживаемых изменений.
2. `bbs-fsp` собирается в отдельный JAR.
3. Каждый из четырёх JAR работает со своей версией: BBS FS 2.6 и 2.7 на Minecraft 1.20.4 и 1.21.11.
4. Dedicated server загружает и сохраняет фильмы с CanvasClip.
5. Работают кадры, слои, RGBA и все заявленные инструменты.
6. Все три render band имеют правильную глубину.
7. Canvas присутствует в preview и всех видах экспорта.
8. Grid/hover/tool preview отсутствуют в экспорте.
9. Нет заметных GL-state leaks, постоянных texture uploads и утечек GPU memory.
10. Совместимость с Iris и IRLights подтверждена тестами.
11. Добавлены и проверены все 19 переводов.
12. Unit и integration tests проходят.

## 23. Формат отчёта модели после каждого этапа

После каждого этапа модель должна сообщить:

```text
1. Что реализовано.
2. Какие файлы изменены.
3. Какие архитектурные решения приняты.
4. Какие тесты запущены и их результат.
5. Какие риски или блокеры остались.
6. Какой следующий этап.
```

Не объявлять работу завершённой только потому, что код компилируется. Для этой функции обязательны проверка рендера, экспорта, dedicated server, локализаций и IRLights.
