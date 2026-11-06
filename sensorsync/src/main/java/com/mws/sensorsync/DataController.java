package com.mws.sensorsync;

import com.mws.sensorsync.exceptions.ApiException;
import com.mws.sensorsync.model.Data;
import com.mws.sensorsync.model.Metadata;
import com.mws.sensorsync.model.Project;
import com.mws.sensorsync.security.AccessService;
import com.mws.sensorsync.services.DataService;
import com.mws.sensorsync.services.MetadataService;
import com.mws.sensorsync.views.CardView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Leitura dos dados (interface humana e interface maquina). Todas as rotas exigem login e
 * so devolvem dados de projetos do usuario (ou de qualquer projeto, para admin).
 * A gravacao de dados fica em ingest/IngestController (autenticada por token de dispositivo).
 */
@RestController
@RequestMapping("/data")
public class DataController {

    private static final long MAX_LENGTH = 10_000;

    private final DataService dataServices;
    private final MetadataService metadataService;
    private final AccessService access;

    public DataController(DataService dataServices, MetadataService metadataService, AccessService access) {
        this.dataServices = dataServices;
        this.metadataService = metadataService;
        this.access = access;
    }

    private static long checkLength(Long length) {
        if (length == null || length < 1 || length > MAX_LENGTH) {
            throw ApiException.badRequest("length deve estar entre 1 e " + MAX_LENGTH);
        }
        return length;
    }

    /** Ultimo valor de cada variavel marcada como card, para um dispositivo. */
    @GetMapping(value = "/cards/{projectID}/device/{device}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CardView> findDataCardsByDevice(@PathVariable("projectID") Long id, @PathVariable("device") Long device) {
        access.requireProject(id);
        List<CardView> cards = new ArrayList<>();
        for (Metadata m : metadataService.findByProjectId(id)) {
            if (!m.isUseCard()) {
                continue;
            }
            Data entity = dataServices.findForCard(id, device, m.getDataIndex());
            CardView card = new CardView();
            card.setSensorIndex(device);
            card.setDataDesc(m.getDataDesc());
            if (entity != null) {
                card.setData(entity.getData());
                card.setReadTime(entity.getReadTime());
                card.setSensorDescription(entity.getSensorDescription());
            }
            cards.add(card);
        }
        return cards;
    }

    /**
     * Series para o grafico: [ [nomes das series], [rotulos do eixo x], [serie 1], [serie 2], ... ].
     * Cada ponto do eixo x e um envio do dispositivo; "" = variavel ausente naquele envio.
     */
    @GetMapping(value = "/charts/{projectID}/device/{device}/length/{length}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<String>> findDataCharts(@PathVariable("projectID") Long id, @PathVariable("device") Long device,
                                             @PathVariable("length") Long length) {
        Project p = access.requireProject(id);
        long n = checkLength(length);
        List<Metadata> metas = metadataService.findByProjectId(id);
        List<Metadata> charted = metas.stream().filter(Metadata::isUseChart).toList();

        // Busca as ultimas n*variaveis leituras do dispositivo e agrupa por envio
        List<Data> recent = new ArrayList<>(dataServices.findByProjectIDandDevice(id, device, n * Math.max(1, p.getDataNumber())));
        recent.sort(Comparator.comparingLong(Data::getId));
        Map<Long, Integer> column = new HashMap<>();
        for (int i = 0; i < charted.size(); i++) {
            column.put(charted.get(i).getDataIndex(), i + 1);
        }
        List<String[]> rows = group(recent, column, charted.size() + 1);
        if (rows.size() > n) {
            rows = rows.subList(rows.size() - (int) n, rows.size());
        }

        List<List<String>> result = new ArrayList<>();
        result.add(charted.stream().map(Metadata::getDataDesc).toList());
        result.add(rows.stream().map(r -> r[0]).toList());
        for (int c = 1; c <= charted.size(); c++) {
            final int col = c;
            result.add(rows.stream().map(r -> r[col] == null ? "" : r[col]).toList());
        }
        return result;
    }

    /** Uma variavel do projeto, como o dashboard precisa. */
    public record SeriesVariable(long index, String desc, String unit, boolean card, boolean chart) {
    }

    /** Um envio do dispositivo: id da ultima linha gravada, instante (epoch ms) e um valor por variavel (null = ausente). */
    public record SeriesPoint(long id, Long t, String time, Double[] v) {
    }

    public record Series(List<SeriesVariable> variables, List<SeriesPoint> points) {
    }

    private static final java.time.format.DateTimeFormatter READ_TIME =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private static Long epochMillis(String readTime) {
        try {
            return java.time.LocalDateTime.parse(readTime, READ_TIME)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serie temporal para o dashboard (cards + graficos em uma unica chamada).
     * ?limit=N : ultimos N envios. ?after=ID : so envios gravados depois do id informado (atualizacao incremental).
     */
    @GetMapping(value = "/series/{projectID}/device/{device}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Series series(@PathVariable("projectID") Long id, @PathVariable("device") Long device,
                         @RequestParam(value = "limit", defaultValue = "50") Long limit,
                         @RequestParam(value = "after", defaultValue = "0") Long after) {
        Project p = access.requireProject(id);
        long n = checkLength(limit);
        List<Metadata> metas = metadataService.findByProjectId(id);
        List<SeriesVariable> vars = metas.stream()
                .map(m -> new SeriesVariable(m.getDataIndex(), m.getDataDesc(), m.getUnit(), m.isUseCard(), m.isUseChart()))
                .toList();
        Map<Long, Integer> column = new HashMap<>();
        for (int i = 0; i < metas.size(); i++) {
            column.put(metas.get(i).getDataIndex(), i);
        }

        return new Series(vars, buildPoints(id, device, n, after, metas.size(), column, p.getDataNumber()));
    }

    /** Ultimo valor conhecido de uma variavel (fora da janela do grafico), com o instante. */
    public record LatestValue(Double v, String time) {
    }

    /** Serie de varios dispositivos: pontos e ultimo valor conhecido de cada variavel, por indice de dispositivo. */
    public record MultiSeries(List<SeriesVariable> variables, Map<Long, List<SeriesPoint>> devices,
                              Map<Long, List<LatestValue>> latest) {
    }

    /**
     * Serie de varios dispositivos em uma chamada: /data/series/{projeto}?devices=0,1,2&limit=50&after=ID.
     * Os ids sao globais e crescentes, entao um unico "after" (maior id ja recebido) serve para todos.
     * Na carga inicial (after=0) tambem devolve o ultimo valor conhecido de cada variavel por dispositivo.
     */
    @GetMapping(value = "/series/{projectID}", produces = MediaType.APPLICATION_JSON_VALUE)
    public MultiSeries multiSeries(@PathVariable("projectID") Long id,
                                   @RequestParam("devices") List<Long> devices,
                                   @RequestParam(value = "limit", defaultValue = "50") Long limit,
                                   @RequestParam(value = "after", defaultValue = "0") Long after) {
        Project p = access.requireProject(id);
        long n = checkLength(limit);
        if (devices.size() > 50) {
            throw ApiException.badRequest("No maximo 50 dispositivos por consulta");
        }
        List<Metadata> metas = metadataService.findByProjectId(id);
        List<SeriesVariable> vars = metas.stream()
                .map(m -> new SeriesVariable(m.getDataIndex(), m.getDataDesc(), m.getUnit(), m.isUseCard(), m.isUseChart()))
                .toList();
        Map<Long, Integer> column = new HashMap<>();
        for (int i = 0; i < metas.size(); i++) {
            column.put(metas.get(i).getDataIndex(), i);
        }
        Map<Long, List<SeriesPoint>> byDevice = new LinkedHashMap<>();
        Map<Long, List<LatestValue>> latest = new LinkedHashMap<>();
        for (Long device : new LinkedHashSet<>(devices)) {
            byDevice.put(device, buildPoints(id, device, n, after, metas.size(), column, p.getDataNumber()));
            if (after <= 0) {
                List<LatestValue> l = new ArrayList<>();
                for (Metadata m : metas) {
                    Data d = dataServices.findForCard(id, device, m.getDataIndex());
                    l.add(d == null ? null : new LatestValue(d.getData(), d.getReadTime()));
                }
                latest.put(device, l);
            }
        }
        return new MultiSeries(vars, byDevice, latest);
    }

    /** Busca as ultimas leituras de um dispositivo e agrupa em envios (mesma regra do relatorio). */
    private List<SeriesPoint> buildPoints(long projectId, long device, long n, long after, int width,
                                          Map<Long, Integer> column, long dataNumber) {
        List<Data> rows = new ArrayList<>(dataServices.findRecentAfter(projectId, device, Math.max(0, after),
                n * Math.max(1, dataNumber)));
        rows.sort(Comparator.comparingLong(Data::getId));
        List<SeriesPoint> points = new ArrayList<>();
        Double[] values = null;
        String time = null;
        long lastId = 0;
        for (Data d : rows) {
            Integer col = column.get(d.getDataIndex());
            if (col == null) {
                continue;
            }
            if (values == null || !Objects.equals(time, d.getReadTime()) || values[col] != null) {
                if (values != null) {
                    points.add(new SeriesPoint(lastId, epochMillis(time), time, values));
                }
                values = new Double[width];
                time = d.getReadTime();
            }
            values[col] = d.getData();
            lastId = d.getId();
        }
        if (values != null) {
            points.add(new SeriesPoint(lastId, epochMillis(time), time, values));
        }
        if (points.size() > n) {
            points = new ArrayList<>(points.subList(points.size() - (int) n, points.size()));
        }
        return points;
    }

    /**
     * Relatorio completo de um dispositivo: primeira linha = cabecalho ("Instante" + variaveis),
     * demais linhas = um envio por linha (vazio quando a variavel nao veio naquele envio).
     */
    @GetMapping(value = "/report/{projectID}/device/{device}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<String>> findAllDataReport(@PathVariable("projectID") Long id, @PathVariable("device") Long device) {
        access.requireProject(id);
        List<Metadata> metas = metadataService.findByProjectId(id);
        Map<Long, Integer> column = new HashMap<>();
        List<String> header = new ArrayList<>();
        header.add("Instante");
        for (Metadata m : metas) {
            column.put(m.getDataIndex(), header.size());
            header.add(m.getDataDesc());
        }

        List<List<String>> report = new ArrayList<>();
        report.add(header);
        List<Data> all = new ArrayList<>(dataServices.findAllByProjectAndSensor(id, device));
        all.sort(Comparator.comparingLong(Data::getId));
        for (String[] row : group(all, column, header.size())) {
            report.add(toList(row));
        }
        return report;
    }

    /**
     * Agrupa leituras (ordenadas por id) em linhas: uma linha por envio. Comeca uma nova linha
     * quando muda o instante ou quando a variavel ja foi preenchida na linha atual.
     */
    private static List<String[]> group(List<Data> sorted, Map<Long, Integer> column, int width) {
        List<String[]> rows = new ArrayList<>();
        String[] row = null;
        for (Data d : sorted) {
            Integer col = column.get(d.getDataIndex());
            if (col == null) {
                continue;
            }
            if (row == null || !Objects.equals(row[0], d.getReadTime()) || row[col] != null) {
                row = new String[width];
                row[0] = d.getReadTime();
                rows.add(row);
            }
            row[col] = String.valueOf(d.getData());
        }
        return rows;
    }

    private static List<String> toList(String[] row) {
        List<String> l = new ArrayList<>(row.length);
        for (String s : row) {
            l.add(s == null ? "" : s);
        }
        return l;
    }

    /*
     * Interface maquina (M2M). Para scripts externos, use HTTP Basic com usuario e senha da plataforma:
     *   curl -u login:senha http://servidor:8080/data/project/1/length/10
     */
    @GetMapping(value = "project/{project_id}/length/{n}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findByProjectID(@PathVariable("project_id") Long projectId, @PathVariable("n") Long length) {
        access.requireProject(projectId);
        return dataServices.findByProject(projectId, checkLength(length));
    }

    @GetMapping(value = "project/{project_id}/device/{y}/length/{n}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findByProjectIDandDevice(@PathVariable("project_id") Long projectId, @PathVariable("y") Long device,
                                               @PathVariable("n") Long length) {
        access.requireProject(projectId);
        return dataServices.findByProjectIDandDevice(projectId, device, checkLength(length));
    }

    @GetMapping(value = "project/{project_id}/device/{y}/data/{z}/length/{n}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Data> findByProjectIDandDeviceandData(@PathVariable("project_id") Long projectId, @PathVariable("y") Long device,
                                                      @PathVariable("z") Long dataIndex, @PathVariable("n") Long length) {
        access.requireProject(projectId);
        return dataServices.findByProjectIDandDeviceandData(projectId, device, dataIndex, checkLength(length));
    }
}
