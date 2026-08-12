package com.odoomaster.ticketing.catalog.internal;

import com.odoomaster.ticketing.catalog.SeatCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds demo events, categories, ticket types and seats for the {@code catalog}
 * module. Runs after
 * {@code iam} seeding; only populates when no events exist yet.
 */
@Component
@Order(2)
public class CatalogDataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(CatalogDataSeeder.class);

  private final EventRepository events;
  private final EventCategoryRepository eventCategories;
  private final EventSeatRepository seats;
  private final TicketTypeRepository ticketTypes;
  private final SeatCatalogService catalog;

  public CatalogDataSeeder(EventRepository events, EventCategoryRepository eventCategories,
      EventSeatRepository seats, TicketTypeRepository ticketTypes,
      SeatCatalogService catalog) {
    this.events = events;
    this.eventCategories = eventCategories;
    this.seats = seats;
    this.ticketTypes = ticketTypes;
    this.catalog = catalog;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (events.count() == 0) {
      seedEvent("Đại nhạc hội Mùa Hè 2026", "🎵 Concert", "DV Entertainment",
          "Đại tiệc âm nhạc hoành tráng với dàn line-up trong nước và quốc tế. Sân khấu LED 360° và pháo hoa cuối show.",
          "SVĐ Mỹ Đình · Hà Nội",
          "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=70",
          plusDays(45),
          new SectionSpec("VIP", 2, 8, new BigDecimal("2500000")),
          new SectionSpec("Hạng A", 4, 10, new BigDecimal("1500000")),
          new SectionSpec("Hạng B", 4, 12, new BigDecimal("800000")));

      seedEvent("AI & Tương lai Công nghệ", "🎓 Seminar", "TechHub VN",
          "Hội thảo công nghệ với 12 diễn giả về LLM, agent, và ứng dụng AI trong doanh nghiệp Việt Nam.",
          "Trung tâm Hội nghị Quốc gia · Hà Nội",
          "https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1200&q=70",
          plusDays(20),
          new SectionSpec("Standard", 3, 10, new BigDecimal("300000")),
          new SectionSpec("Premium", 2, 6, new BigDecimal("700000")));

      seedEvent("Nhiếp ảnh chân dung cơ bản", "🛠 Workshop", "Chụp Đi Studio",
          "Workshop 4 giờ hướng dẫn ánh sáng tự nhiên, đặt máy và hướng người mẫu. Mang theo máy ảnh.",
          "Hub Hub Coworking · Quận 1, TP.HCM",
          "https://images.unsplash.com/photo-1505373877841-8d25f7d46678?auto=format&fit=crop&w=1200&q=70",
          plusDays(14),
          new SectionSpec("Workshop", 3, 8, new BigDecimal("800000")));

      seedEvent("Liên hoan Ẩm thực Đường phố", "🎬 Festival", "Saigon Foodie Co.",
          "Festival ẩm thực đường phố quy tụ 60+ gian hàng từ Bắc tới Nam. Vé bao gồm phiếu thử đồ ăn.",
          "Phố đi bộ Nguyễn Huệ · TP.HCM",
          "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?auto=format&fit=crop&w=1200&q=70",
          plusDays(60),
          new SectionSpec("General", 5, 12, new BigDecimal("150000")));

      seedEvent("Giao hữu V-League · CAHN vs HN FC", "🏆 Thể thao", "Liên đoàn bóng đá VN",
          "Trận giao hữu giữa Công An Hà Nội và Hà Nội FC, mở bán toàn bộ khán đài.",
          "SVĐ Hàng Đẫy · Hà Nội",
          "https://images.unsplash.com/photo-1521412644187-c49fa049e84d?auto=format&fit=crop&w=1200&q=70",
          plusDays(10),
          new SectionSpec("Khán đài A", 4, 12, new BigDecimal("200000")),
          new SectionSpec("Khán đài B", 4, 12, new BigDecimal("150000")));

      seedEvent("Vở nhạc kịch \"Tiên Tri\"", "🎭 Nghệ thuật", "Nhà hát Tuổi Trẻ",
          "Vở nhạc kịch hoành tráng với dàn diễn viên 40 người. 3 màn, dài 2 tiếng.",
          "Nhà hát Lớn · Hà Nội",
          "https://images.unsplash.com/photo-1503095396549-807759245b35?auto=format&fit=crop&w=1200&q=70",
          plusDays(70),
          new SectionSpec("Tầng 1", 4, 10, new BigDecimal("450000")),
          new SectionSpec("Tầng 2", 3, 12, new BigDecimal("280000")));

      seedBulkExtra();
      log.info("Seeded {} demo events", events.count());
    }
  }

  private static final String[] IMG_CONCERT = {
      "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1429962714451-bb934ecdc4ec?auto=format&fit=crop&w=1200&q=70",
  };
  private static final String[] IMG_SEMINAR = {
      "https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1591115765373-5207764f72e7?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1475721027785-f74eccf877e2?auto=format&fit=crop&w=1200&q=70",
  };
  private static final String[] IMG_WORKSHOP = {
      "https://images.unsplash.com/photo-1505373877841-8d25f7d46678?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1517048676732-d65bc937f952?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1559223607-a43c990c692c?auto=format&fit=crop&w=1200&q=70",
  };
  private static final String[] IMG_FESTIVAL = {
      "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=70",
  };
  private static final String[] IMG_SPORTS = {
      "https://images.unsplash.com/photo-1521412644187-c49fa049e84d?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1517649763962-0c623066013b?auto=format&fit=crop&w=1200&q=70",
  };
  private static final String[] IMG_ART = {
      "https://images.unsplash.com/photo-1503095396549-807759245b35?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1551928831-3b2b3b87a3da?auto=format&fit=crop&w=1200&q=70",
      "https://images.unsplash.com/photo-1499364615650-ec38552f4f34?auto=format&fit=crop&w=1200&q=70",
  };

  private static final String[] VENUES_HN = {
      "Nhà hát Lớn · Hà Nội",
      "Trung tâm Hội nghị Quốc gia · Hà Nội",
      "SVĐ Mỹ Đình · Hà Nội",
      "SVĐ Hàng Đẫy · Hà Nội",
      "Cung Văn hóa Hữu nghị Việt Xô · Hà Nội",
      "Trường ĐH Bách Khoa · Hà Nội",
      "FPT Tower · Cầu Giấy, Hà Nội",
      "Pan Pacific Hotel · Hà Nội",
  };
  private static final String[] VENUES_HCM = {
      "Nhà hát Bến Thành · TP.HCM",
      "Hub Hub Coworking · Quận 1, TP.HCM",
      "GEM Center · Quận 1, TP.HCM",
      "SVĐ Thống Nhất · TP.HCM",
      "Dreamplex · Quận 1, TP.HCM",
      "Reverie Saigon · TP.HCM",
      "Nhà thi đấu Phú Thọ · TP.HCM",
      "Saigon Innovation Hub · Quận 3, TP.HCM",
  };
  private static final String[] VENUES_DN = {
      "Nhà hát Trưng Vương · Đà Nẵng",
      "Cung Thể thao Tiên Sơn · Đà Nẵng",
      "Furama Resort · Đà Nẵng",
  };

  private record Template(String category, String organizer, String[] titles, String[] images,
      int[] priceRange, int sectionCountMax) {
  }

  private static final Template[] TEMPLATES = {
      new Template("🎵 Concert", "DV Entertainment", new String[] {
          "Đêm nhạc Acoustic · %s",
          "Live Concert · %s 2026",
          "Tour diễn toàn quốc · %s",
          "Đại nhạc hội · %s",
          "Indie Night · %s",
          "Acoustic & Storytelling · %s",
          "Symphony Live · %s",
          "Mini Show · %s tại Hà Nội",
          "Mini Show · %s tại TP.HCM",
          "Liveshow kỷ niệm 10 năm · %s",
      }, IMG_CONCERT, new int[] { 300_000, 2_500_000 }, 3),

      new Template("🎓 Seminar", "TechHub VN", new String[] {
          "Hội thảo · Quản trị tài chính cá nhân",
          "Hội thảo · Đầu tư chứng khoán cho người mới",
          "Hội thảo · Phân tích kỹ thuật BTC/ETH",
          "Diễn đàn · AI trong y tế",
          "Diễn đàn · An ninh mạng 2026",
          "Hội thảo · Khởi nghiệp công nghệ",
          "Hội thảo · ESG và doanh nghiệp Việt",
          "Hội nghị · Phát triển nhân sự Gen Z",
          "Diễn đàn · Logistics Đông Nam Á",
          "Hội thảo · Pháp lý cho startup",
      }, IMG_SEMINAR, new int[] { 0, 800_000 }, 2),

      new Template("🛠 Workshop", "Chụp Đi Studio", new String[] {
          "Workshop · Nhiếp ảnh phong cảnh",
          "Workshop · Lightroom & Photoshop nâng cao",
          "Workshop · Pha chế cà phê specialty",
          "Workshop · Bánh ngọt Pháp tại gia",
          "Workshop · Vẽ acrylic cho người mới",
          "Workshop · Hand-lettering tiếng Việt",
          "Workshop · Yoga vinyasa căn bản",
          "Workshop · Coding cho trẻ em",
          "Workshop · Mixology & cocktail tại nhà",
          "Workshop · Gốm sứ thủ công",
      }, IMG_WORKSHOP, new int[] { 200_000, 1_500_000 }, 1),

      new Template("🎬 Festival", "Saigon Foodie Co.", new String[] {
          "Festival · Ẩm thực đường phố Tết",
          "Festival · Bia thủ công Sài Gòn",
          "Festival · Văn hóa Hàn Quốc",
          "Festival · Anime & Cosplay",
          "Festival · Cà phê Việt 2026",
          "Festival · Hoa Đà Lạt",
          "Lễ hội · Đèn lồng Hội An",
          "Lễ hội · Ánh sáng Hồ Tây",
          "Festival · Indie Film Sài Gòn",
          "Festival · Âm nhạc đường phố",
      }, IMG_FESTIVAL, new int[] { 0, 600_000 }, 2),

      new Template("🏆 Thể thao", "Liên đoàn bóng đá VN", new String[] {
          "V-League · CAHN vs SHB Đà Nẵng",
          "V-League · HAGL vs Viettel",
          "V-League · Bình Định vs Nam Định",
          "Cúp Quốc gia · Tứ kết lượt đi",
          "Giải Tennis VTF Masters · Hà Nội",
          "Giải Cầu lông toàn quốc · TP.HCM",
          "Vô địch Boxing Quốc gia",
          "Đại hội Esports VN · LMHT chung kết",
          "Pickleball Open · TP.HCM",
          "Marathon Hà Nội 2026 · Pasta party",
      }, IMG_SPORTS, new int[] { 100_000, 800_000 }, 3),

      new Template("🎭 Nghệ thuật", "Nhà hát Tuổi Trẻ", new String[] {
          "Vở kịch · \"Hà Nội mùa thu\"",
          "Vở chèo · \"Quan Âm Thị Kính\"",
          "Vở cải lương · \"Lan và Điệp\"",
          "Múa đương đại · \"Sông quê\"",
          "Triển lãm · Tranh sơn dầu Đỗ Hoàng Tường",
          "Triển lãm · Nhiếp ảnh đường phố",
          "Ballet · \"Hồ thiên nga\"",
          "Nhạc kịch · \"Tiếng vọng\"",
          "Đêm thơ · Hàn Mặc Tử",
          "Múa rối nước · Đoàn Thăng Long",
      }, IMG_ART, new int[] { 150_000, 700_000 }, 2),
  };

  private static final String[] DESCRIPTIONS = {
      "Sự kiện đặc sắc với chương trình nghệ thuật chuyên nghiệp, dàn dựng công phu và trải nghiệm khán giả tối ưu.",
      "Đêm diễn được mong chờ nhất trong tháng. Vé đã bao gồm chỗ ngồi cố định theo sơ đồ và ưu đãi đồ uống.",
      "Chương trình quy tụ những tên tuổi hàng đầu, hứa hẹn mang đến trải nghiệm khó quên cho khán giả.",
      "Sự kiện do đơn vị uy tín tổ chức, tuân thủ đầy đủ quy định an toàn và có dịch vụ hỗ trợ tận tâm.",
      "Một buổi tối khó quên với âm thanh ánh sáng tiêu chuẩn quốc tế và đội ngũ vận hành chuyên nghiệp.",
  };

  private void seedBulkExtra() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    String[][] venuesByCity = { VENUES_HN, VENUES_HCM, VENUES_DN };

    for (Template tpl : TEMPLATES) {
      int sampleCount = Math.min(tpl.titles().length, 9);
      for (int i = 0; i < sampleCount; i++) {
        String rawTitle = tpl.titles()[i];
        String title = rawTitle.contains("%s")
            ? String.format(rawTitle, ARTISTS[rnd.nextInt(ARTISTS.length)])
            : rawTitle;
        String[] venuePool = venuesByCity[rnd.nextInt(venuesByCity.length)];
        String venue = venuePool[rnd.nextInt(venuePool.length)];
        String image = tpl.images()[rnd.nextInt(tpl.images().length)];
        String desc = DESCRIPTIONS[rnd.nextInt(DESCRIPTIONS.length)];
        int days = 3 + rnd.nextInt(150);
        int sectionCount = 1 + rnd.nextInt(Math.max(1, tpl.sectionCountMax()));
        SectionSpec[] sections = buildSections(tpl, sectionCount, rnd);

        seedEvent(title, tpl.category(), tpl.organizer(), desc, venue, image,
            plusDays(days), sections);
      }
    }
  }

  private SectionSpec[] buildSections(Template tpl, int count, ThreadLocalRandom rnd) {
    String[] sectionNames = sectionNamesFor(tpl.category());
    int lo = tpl.priceRange()[0];
    int hi = tpl.priceRange()[1];

    SectionSpec[] out = new SectionSpec[count];
    for (int i = 0; i < count; i++) {
      String name = sectionNames[Math.min(i, sectionNames.length - 1)];
      int rows = 2 + rnd.nextInt(4);
      int seatsPerRow = 8 + rnd.nextInt(7);
      BigDecimal price = BigDecimal.valueOf(
          nearestRound(lo + rnd.nextInt(Math.max(1, hi - lo + 1)), 50_000));
      out[i] = new SectionSpec(name, rows, seatsPerRow, price);
    }
    return out;
  }

  private static int nearestRound(int v, int step) {
    return Math.max(0, Math.round((float) v / step) * step);
  }

  private static String[] sectionNamesFor(String category) {
    if (category.contains("Thể thao"))
      return new String[] { "Khán đài A", "Khán đài B", "Khán đài C" };
    if (category.contains("Workshop"))
      return new String[] { "Workshop", "Mở rộng" };
    if (category.contains("Festival"))
      return new String[] { "General", "Front Stage" };
    if (category.contains("Seminar"))
      return new String[] { "Standard", "Premium" };
    if (category.contains("Nghệ thuật"))
      return new String[] { "Tầng 1", "Tầng 2", "Lô" };
    return new String[] { "VIP", "Hạng A", "Hạng B" };
  }

  private static final String[] ARTISTS = {
      "Sơn Tùng M-TP", "Đen Vâu", "Mỹ Tâm", "Hà Anh Tuấn", "Vũ.", "Hoàng Dũng",
      "MONO", "AMEE", "tlinh", "MIN", "Hoàng Thùy Linh", "Phan Mạnh Quỳnh",
      "GreyD", "Bích Phương", "Văn Mai Hương", "Justatee",
  };

  private Instant plusDays(long days) {
    return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  }

  private void seedEvent(String title, String category, String organizer,
      String desc, String location, String imageUrl,
      Instant start, SectionSpec... sections) {
    EventCategory cat = eventCategories.findByName(category)
        .orElseGet(() -> eventCategories.save(EventCategory.named(category)));
    Set<EventCategory> catSet = new HashSet<>();
    catSet.add(cat);

    Event e = Event.draft(title, desc, location, organizer, imageUrl,
        start, start.plus(3, ChronoUnit.HOURS), Instant.now());
    e.categorise(catSet);
    // The demo catalog ships on sale; seats are added immediately below.
    e.publish(1);
    events.save(e);
    var venue = catalog.ensureVenue(location, null);
    List<EventSeat> toSave = new ArrayList<>();
    for (SectionSpec sec : sections) {
      var sectionRow = catalog.ensureSection(venue.getId(), sec.name);
      int qty = sec.rows * sec.seatsPerRow;
      TicketType tt = ticketTypes.findByEventIdAndName(e.getId(), sec.name)
          .orElseGet(() -> ticketTypes.save(TicketType.create(e.getId(), sec.name, sec.price, qty)));
      for (int r = 0; r < sec.rows; r++) {
        char rowLabel = (char) ('A' + r);
        for (int n = 1; n <= sec.seatsPerRow; n++) {
          String rl = String.valueOf(rowLabel);
          String sn = String.format("%02d", n);
          var seat = catalog.ensureSeat(sectionRow.getId(), rl, sn);
          toSave.add(EventSeat.create(e.getId(), seat.getId(), tt.getId(),
              sec.name, rl, sn, sec.price));
        }
      }
    }
    seats.saveAll(toSave);
  }

  private record SectionSpec(String name, int rows, int seatsPerRow, BigDecimal price) {
  }
}
