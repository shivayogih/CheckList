# Checklist templates backlog (CL-319)

The India master catalogue ships 15 `checklist_templates`. The app has no templates feature yet
(a template would pre-fill a checklist from category ids), so they are **not** in the seed and
no UI exists for them. They are kept here, generated from the catalogue by
`python3 tools/seed/build_india_catalog.py`, as the backlog for issue CL-319.

Category ids map to seed keys as described in [seed-catalog.md](seed-catalog.md).

| Template | Name | Frequency | Categories (seed key) | Guidance |
|---|---|---|---|---|
| `TPL001` | Weekly Grocery Shopping | weekly | Groceries & Staples (`groceries`), Vegetables (`vegetables`), Fruits (`fruits`), Leafy Greens & Herbs (`cat004`), Dairy & Eggs (`cat005`), Spices, Oils & Condiments (`cat009`), Beverages (`cat010`) | Check pantry first; select quantities; mark items already at home. |
| `TPL002` | Vegetables & Fruits Refill | weekly | Vegetables (`vegetables`), Fruits (`fruits`), Leafy Greens & Herbs (`cat004`) | Choose seasonal produce; use kg/g or pc/bunch according to shop. |
| `TPL003` | Monthly Household Supplies | monthly | Personal Care (`toiletries`), Cleaning & Laundry (`cat013`), Disposable Daily Needs (`cat014`), Kitchen & Dining Supplies (`cat015`), Home Maintenance & Utilities (`cat016`) | Review current stock before buying. |
| `TPL004` | Non-Vegetarian Shopping | weekly | Meat, Poultry & Seafood (`cat006`), Dairy & Eggs (`cat005`) | Keep chilled/frozen items safe; note weight and portion. |
| `TPL005` | Office Workday Checklist | daily | Office & Stationery (`stationery`), Electronics & Accessories (`electronics`), Digital & Recurring Tasks (`cat030`) | Check laptop, charger, access, meetings, and task priorities. |
| `TPL006` | Travel Packing Checklist | once-per-trip | Travel & Packing (`travel`), Clothing & Footwear (`clothing`), Medicine & First Aid (`medicines`), Documents & Admin (`documents`) | Duplicate for each trip and customize by weather and destination. |
| `TPL007` | Medical Appointment Prep | as-needed | Medicine & First Aid (`medicines`), Documents & Admin (`documents`) | Bring relevant records and medication list; follow clinician instructions. |
| `TPL008` | Exam Preparation | daily | Study & Exams (`exam`), Documents & Admin (`documents`) | Track syllabus, revision, mock tests, and required documents. |
| `TPL009` | Home Cleaning Routine | weekly | Cleaning & Laundry (`cat013`), Disposable Daily Needs (`cat014`), Home Maintenance & Utilities (`cat016`) | Assign tasks to household members and set recurring frequency. |
| `TPL010` | Baby Essentials Refill | weekly | Baby & Kids (`cat018`), Disposable Daily Needs (`cat014`) | Adjust to the child's age and caregiver guidance. |
| `TPL011` | Pet Care Routine | daily | Pet Supplies (`cat019`) | Use veterinarian instructions for prescribed products. |
| `TPL012` | Vehicle Monthly Check | monthly | Vehicle & Commute (`cat028`), Documents & Admin (`documents`) | Track service dates, insurance, documents, and safety checks. |
| `TPL013` | Festival / Event Planning | once-per-event | Events & Celebrations (`cat029`), Disposable Daily Needs (`cat014`), Kitchen & Dining Supplies (`cat015`) | Estimate guests and quantities; assign owners. |
| `TPL014` | Garden & Farm Tasks | weekly | Gardening & Agriculture (`cat027`) | Adapt tasks to crop, season, soil, water availability, and local advice. |
| `TPL015` | Digital Security & Bills | monthly | Digital & Recurring Tasks (`cat030`), Documents & Admin (`documents`) | Avoid storing passwords, OTPs, or full sensitive IDs in checklist notes. |
